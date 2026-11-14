package com.scout.springserver.service;

import com.scout.springserver.client.Coordinates;
import com.scout.springserver.client.NominatimClient;
import com.scout.springserver.client.OtpClient;
import com.scout.springserver.client.OtpClient.TripPlan;
import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.UniversityCommuteView;
import com.scout.springserver.exception.DomainExceptions.NoGeocodedSpacesException;
import com.scout.springserver.exception.DomainExceptions.UniversityAddressNotFoundException;
import com.scout.springserver.exception.DomainExceptions.UniversityNotConfiguredException;
import com.scout.springserver.model.Space;
import com.scout.springserver.model.University;
import com.scout.springserver.util.JsonColumns;
import com.scout.springserver.util.SqlValues;
import com.scout.springserver.util.Timestamps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** backend/src/services/university.service.ts */
@Service
public class UniversityService {

    private static final Logger log = LoggerFactory.getLogger(UniversityService.class);
    private static final int OTP_CONCURRENCY = 3;
    private static final int ROWS_PER_INSERT = 500;

    private record ComputedCommute(int universityId, int spaceId, TripPlan trip) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final NominatimClient nominatim;
    private final OtpClient otp;
    private final ExecutorService executor;

    public UniversityService(
            JdbcClient jdbc,
            TransactionTemplate tx,
            NominatimClient nominatim,
            OtpClient otp,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.nominatim = nominatim;
        this.otp = otp;
        this.executor = executor;
    }

    public Optional<University> getUniversityForUser(int ownerId) {
        return jdbc.sql("SELECT * FROM universities WHERE owner_id = ? LIMIT 1")
                .param(ownerId)
                .query(University.ROW_MAPPER)
                .optional();
    }

    public University saveUniversityForUser(int ownerId, String name, String address) {
        Coordinates geocoded = nominatim.geocodeAddress(address)
                .orElseThrow(UniversityAddressNotFoundException::new);

        University university = tx.execute(status -> {
            University saved = jdbc.sql("""
                            INSERT INTO universities (owner_id, name, address, lat, lng)
                            VALUES (:ownerId, :name, :address, :lat, :lng)
                            ON CONFLICT (owner_id) DO UPDATE SET
                                name = :name,
                                address = :address,
                                lat = :lat,
                                lng = :lng,
                                updated_at = :now
                            RETURNING *
                            """)
                    .param("ownerId", ownerId)
                    .param("name", name)
                    .param("address", address)
                    .param("lat", geocoded.lat())
                    .param("lng", geocoded.lng())
                    .param("now", Timestamps.nowUtc())
                    .query(University.ROW_MAPPER)
                    .single();

            // Existing commutes refer to the previous destination coordinates.
            jdbc.sql("DELETE FROM university_commutes WHERE university_id = ?").param(saved.id()).update();
            return saved;
        });

        log.info("University saved for user {}", ownerId);
        return university;
    }

    public List<UniversityCommuteView> listUniversityCommutesForUser(int ownerId) {
        Optional<University> university = getUniversityForUser(ownerId);
        if (university.isEmpty()) return List.of();

        return jdbc.sql("""
                        SELECT uc.id, uc.space_id,
                               s.name AS space_name, s.address AS space_address,
                               s.lat AS space_lat, s.lng AS space_lng,
                               uc.duration_seconds, uc.walk_duration_seconds, uc.metro_duration_seconds,
                               uc.bus_duration_seconds, uc.tram_duration_seconds, uc.other_duration_seconds,
                               uc.wait_duration_seconds, uc.route_geometry, uc.computed_at
                        FROM university_commutes uc
                        INNER JOIN spaces s ON uc.space_id = s.id
                        WHERE uc.university_id = :universityId AND s.owner_id = :ownerId
                        """)
                .param("universityId", university.get().id())
                .param("ownerId", ownerId)
                .query(UniversityCommuteView.ROW_MAPPER)
                .list();
    }

    public List<UniversityCommuteView> computeUniversityCommutesForUser(int ownerId) {
        University university = getUniversityForUser(ownerId).orElseThrow(UniversityNotConfiguredException::new);

        List<Space> geocodedSpaces = jdbc.sql("""
                        SELECT * FROM spaces
                        WHERE owner_id = ? AND lat IS NOT NULL AND lng IS NOT NULL
                        """)
                .param(ownerId)
                .query(Space.ROW_MAPPER)
                .list();

        if (geocodedSpaces.isEmpty()) throw new NoGeocodedSpacesException();

        Coordinates destination = new Coordinates(university.lat(), university.lng());
        List<ComputedCommute> computed = new ArrayList<>();

        for (int i = 0; i < geocodedSpaces.size(); i += OTP_CONCURRENCY) {
            List<Space> batch = geocodedSpaces.subList(i, Math.min(geocodedSpaces.size(), i + OTP_CONCURRENCY));
            List<CompletableFuture<ComputedCommute>> futures = new ArrayList<>();
            for (Space space : batch) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    if (!space.hasCoordinates()) return null;
                    return otp.planTripWithGeometry(new Coordinates(space.lat(), space.lng()), destination)
                            .map(trip -> new ComputedCommute(university.id(), space.id(), trip))
                            .orElse(null);
                }, executor));
            }
            RoutingService.joinAll(futures).stream().filter(c -> c != null).forEach(computed::add);
        }

        tx.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM university_commutes WHERE university_id = ?").param(university.id()).update();

            for (List<ComputedCommute> chunk : SqlValues.chunks(computed, ROWS_PER_INSERT)) {
                List<Object> params = new ArrayList<>();
                for (ComputedCommute c : chunk) {
                    TripPlan t = c.trip();
                    params.add(c.universityId());
                    params.add(c.spaceId());
                    params.add(t.durationSeconds());
                    params.add(t.walkDurationSeconds());
                    params.add(t.metroDurationSeconds());
                    params.add(t.busDurationSeconds());
                    params.add(t.tramDurationSeconds());
                    params.add(t.otherDurationSeconds());
                    params.add(t.waitDurationSeconds());
                    params.add(JsonColumns.writeLegs(t.routeGeometry()));
                }
                jdbc.sql("""
                                INSERT INTO university_commutes (university_id, space_id, duration_seconds,
                                    walk_duration_seconds, metro_duration_seconds, bus_duration_seconds,
                                    tram_duration_seconds, other_duration_seconds, wait_duration_seconds,
                                    route_geometry)
                                VALUES """ + " "
                                + SqlValues.tuples("(?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))", chunk.size()))
                        .params(params)
                        .update();
            }
        });

        log.info("Computed {} university commutes for user {}", computed.size(), ownerId);
        return listUniversityCommutesForUser(ownerId);
    }
}
