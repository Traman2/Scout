package com.scout.springserver.service;

import com.scout.springserver.client.Coordinates;
import com.scout.springserver.client.OtpClient;
import com.scout.springserver.client.OtpClient.TripPlan;
import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.SpaceRouteView;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Route;
import com.scout.springserver.util.Errors;
import com.scout.springserver.util.JsonColumns;
import com.scout.springserver.util.SqlValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** backend/src/services/routing.service.ts */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    // Firing a trip for every POI at once would overload the resource-capped
    // local OTP instance. Route one small batch at a time instead.
    static final int OTP_CONCURRENCY = 3;
    private static final int ROWS_PER_INSERT = 500;

    @FunctionalInterface
    public interface BatchProgressListener {
        void onBatchProgress(int completed, int total);
    }

    private record PlannedRoute(int spaceId, int poiId, TripPlan trip) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final OtpClient otp;
    private final ExecutorService executor;

    public RoutingService(
            JdbcClient jdbc,
            TransactionTemplate tx,
            OtpClient otp,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.otp = otp;
        this.executor = executor;
    }

    private PlannedRoute planTripForPoi(int spaceId, double spaceLat, double spaceLng, Poi poi, String progress) {
        log.info("[{}] Planning trip: POI {} ({}) -> space {}", progress, poi.id(), poi.name(), spaceId);

        Optional<TripPlan> trip = otp.planTripWithGeometry(
                new Coordinates(poi.lat(), poi.lng()), new Coordinates(spaceLat, spaceLng));

        if (trip.isEmpty()) {
            log.warn("No itinerary found from POI {} to space {}", poi.id(), spaceId);
            return null;
        }

        log.info("[{}] Done: POI {} -> space {}, {}s", progress, poi.id(), spaceId, trip.get().durationSeconds());
        return new PlannedRoute(spaceId, poi.id(), trip.get());
    }

    /**
     * Per-space delete-and-replace of routes (same convention as POI discovery),
     * now inside a transaction so a failure can't leave a space with zero routes.
     */
    public List<Route> computeRoutesForSpace(
            int spaceId, double spaceLat, double spaceLng, List<Poi> targets, BatchProgressListener onBatchProgress) {
        log.info("Computing routes for space {}: {} POIs, {} at a time", spaceId, targets.size(), OTP_CONCURRENCY);

        List<PlannedRoute> planned = new ArrayList<>();
        int plannedCount = 0;

        for (int i = 0; i < targets.size(); i += OTP_CONCURRENCY) {
            List<Poi> batch = targets.subList(i, Math.min(targets.size(), i + OTP_CONCURRENCY));
            List<CompletableFuture<PlannedRoute>> futures = new ArrayList<>();
            for (int j = 0; j < batch.size(); j++) {
                Poi poi = batch.get(j);
                String progress = (i + j + 1) + "/" + targets.size();
                futures.add(CompletableFuture.supplyAsync(
                        () -> planTripForPoi(spaceId, spaceLat, spaceLng, poi, progress), executor));
            }

            planned.addAll(joinAll(futures));
            plannedCount += batch.size();
            if (onBatchProgress != null) onBatchProgress.onBatchProgress(plannedCount, targets.size());
        }

        List<PlannedRoute> computed = planned.stream().filter(r -> r != null).toList();

        List<Route> inserted = tx.execute(status -> {
            jdbc.sql("DELETE FROM routes WHERE space_id = ?").param(spaceId).update();
            if (computed.isEmpty()) return List.<Route>of();

            List<Route> rows = new ArrayList<>();
            for (List<PlannedRoute> chunk : SqlValues.chunks(computed, ROWS_PER_INSERT)) {
                List<Object> params = new ArrayList<>();
                for (PlannedRoute r : chunk) {
                    TripPlan t = r.trip();
                    params.add(r.spaceId());
                    params.add(r.poiId());
                    params.add(t.durationSeconds());
                    params.add(t.walkDurationSeconds());
                    params.add(t.metroDurationSeconds());
                    params.add(t.busDurationSeconds());
                    params.add(t.tramDurationSeconds());
                    params.add(t.otherDurationSeconds());
                    params.add(t.waitDurationSeconds());
                    params.add(JsonColumns.writeLegs(t.routeGeometry()));
                }
                rows.addAll(jdbc.sql("""
                                INSERT INTO routes (space_id, poi_id, duration_seconds, walk_duration_seconds,
                                    metro_duration_seconds, bus_duration_seconds, tram_duration_seconds,
                                    other_duration_seconds, wait_duration_seconds, route_geometry)
                                VALUES """ + " "
                                + SqlValues.tuples("(?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))", chunk.size())
                                + " RETURNING *")
                        .params(params)
                        .query(Route.ROW_MAPPER)
                        .list());
            }
            return rows;
        });

        if (inserted == null || inserted.isEmpty()) {
            log.info("No routes computed for space {}", spaceId);
            return List.of();
        }

        log.info("Computed {} routes for space {}", inserted.size(), spaceId);
        return inserted;
    }

    public List<SpaceRouteView> listRoutesForSpace(int spaceId) {
        return jdbc.sql("""
                        SELECT r.id, r.poi_id, p.name AS poi_name, p.category AS poi_category,
                               r.duration_seconds, r.walk_duration_seconds, r.metro_duration_seconds,
                               r.bus_duration_seconds, r.tram_duration_seconds, r.other_duration_seconds,
                               r.wait_duration_seconds, r.route_geometry, r.computed_at
                        FROM routes r
                        INNER JOIN pois p ON r.poi_id = p.id
                        WHERE r.space_id = ?
                        """)
                .param(spaceId)
                .query(SpaceRouteView.ROW_MAPPER)
                .list();
    }

    /** Promise.all: wait for the batch, rethrowing the first failure unwrapped. */
    static <T> List<T> joinAll(List<CompletableFuture<T>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            Throwable cause = Errors.unwrap(e);
            if (cause instanceof RuntimeException re) throw re;
            throw new IllegalStateException(cause);
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }
}
