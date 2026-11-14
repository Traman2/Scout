package com.scout.springserver.service;

import com.scout.springserver.client.Coordinates;
import com.scout.springserver.client.NominatimClient;
import com.scout.springserver.dto.Requests;
import com.scout.springserver.exception.DomainExceptions.AddressNotFoundException;
import com.scout.springserver.exception.DomainExceptions.SpaceNotFoundException;
import com.scout.springserver.model.Space;
import com.scout.springserver.util.Timestamps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** backend/src/services/space.service.ts */
@Service
public class SpaceService {

    private static final Logger log = LoggerFactory.getLogger(SpaceService.class);

    public record SpaceResult(Space space, GeocodeAttempt geocodeAttempt) {}

    private final JdbcClient jdbc;
    private final NominatimClient nominatim;

    public SpaceService(JdbcClient jdbc, NominatimClient nominatim) {
        this.jdbc = jdbc;
        this.nominatim = nominatim;
    }

    private GeocodeAttempt tryGeocode(String address) {
        try {
            Optional<Coordinates> result = nominatim.geocodeAddress(address);
            if (result.isEmpty()) {
                log.warn("Geocoding found no match for a space address");
                return new GeocodeAttempt.Failed(new AddressNotFoundException());
            }
            return new GeocodeAttempt.Succeeded(result.get());
        } catch (RuntimeException e) {
            log.warn("Geocoding request failed, continuing without coordinates");
            return new GeocodeAttempt.Failed(e);
        }
    }

    public SpaceResult createSpace(int ownerId, String name, String address) {
        GeocodeAttempt geocodeAttempt = tryGeocode(address);
        Coordinates coordinates = geocodeAttempt instanceof GeocodeAttempt.Succeeded s ? s.coordinates() : null;

        Space space = jdbc.sql("""
                        INSERT INTO spaces (owner_id, name, address, lat, lng)
                        VALUES (:ownerId, :name, :address, :lat, :lng)
                        RETURNING *
                        """)
                .param("ownerId", ownerId)
                .param("name", name)
                .param("address", address)
                .param("lat", coordinates != null ? coordinates.lat() : null, java.sql.Types.DOUBLE)
                .param("lng", coordinates != null ? coordinates.lng() : null, java.sql.Types.DOUBLE)
                .query(Space.ROW_MAPPER)
                .single();

        log.info("Space {} created for user {}", space.id(), ownerId);
        return new SpaceResult(space, geocodeAttempt);
    }

    public List<Space> listSpacesForUser(int ownerId) {
        return jdbc.sql("SELECT * FROM spaces WHERE owner_id = :ownerId ORDER BY id")
                .param("ownerId", ownerId)
                .query(Space.ROW_MAPPER)
                .list();
    }

    public Space getSpaceForUser(int id, int ownerId) {
        return jdbc.sql("SELECT * FROM spaces WHERE id = :id AND owner_id = :ownerId LIMIT 1")
                .param("id", id)
                .param("ownerId", ownerId)
                .query(Space.ROW_MAPPER)
                .optional()
                .orElseThrow(SpaceNotFoundException::new);
    }

    public SpaceResult updateSpaceForUser(int id, int ownerId, Requests.UpdateSpace data) {
        GeocodeAttempt geocodeAttempt = data.address() != null ? tryGeocode(data.address()) : null;
        Coordinates coordinates = geocodeAttempt instanceof GeocodeAttempt.Succeeded s ? s.coordinates() : null;

        List<String> sets = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("ownerId", ownerId);

        if (data.name() != null) {
            sets.add("name = :name");
            params.put("name", data.name());
        }
        if (data.address() != null) {
            sets.add("address = :address");
            params.put("address", data.address());
        }

        // Explicit lat/lng (e.g. from the manual-coordinates form) always wins
        // over an address-triggered geocode result.
        Double lat = data.lat() != null ? data.lat() : coordinates != null ? Double.valueOf(coordinates.lat()) : null;
        Double lng = data.lng() != null ? data.lng() : coordinates != null ? Double.valueOf(coordinates.lng()) : null;
        if (lat != null) {
            sets.add("lat = :lat");
            params.put("lat", lat);
        }
        if (lng != null) {
            sets.add("lng = :lng");
            params.put("lng", lng);
        }

        sets.add("updated_at = :updatedAt");
        params.put("updatedAt", Timestamps.nowUtc());

        Space space = jdbc.sql("UPDATE spaces SET " + String.join(", ", sets)
                        + " WHERE id = :id AND owner_id = :ownerId RETURNING *")
                .params(params)
                .query(Space.ROW_MAPPER)
                .optional()
                .orElseThrow(SpaceNotFoundException::new);

        log.info("Space {} updated for user {}", id, ownerId);
        return new SpaceResult(space, geocodeAttempt);
    }

    public Space geocodeSpace(int id, int ownerId) {
        Space space = getSpaceForUser(id, ownerId);

        Coordinates geocoded = nominatim.geocodeAddress(space.address())
                .orElseThrow(AddressNotFoundException::new);

        Space updated = jdbc.sql("""
                        UPDATE spaces SET lat = :lat, lng = :lng, updated_at = :updatedAt
                        WHERE id = :id AND owner_id = :ownerId
                        RETURNING *
                        """)
                .param("lat", geocoded.lat())
                .param("lng", geocoded.lng())
                .param("updatedAt", Timestamps.nowUtc())
                .param("id", id)
                .param("ownerId", ownerId)
                .query(Space.ROW_MAPPER)
                .optional()
                .orElseThrow(SpaceNotFoundException::new);

        log.info("Space {} geocoded for user {}", id, ownerId);
        return updated;
    }

    public int deleteSpaceForUser(int id, int ownerId) {
        Integer deletedId = jdbc.sql("DELETE FROM spaces WHERE id = :id AND owner_id = :ownerId RETURNING id")
                .param("id", id)
                .param("ownerId", ownerId)
                .query(Integer.class)
                .optional()
                .orElseThrow(SpaceNotFoundException::new);

        log.info("Space {} deleted for user {}", id, ownerId);
        return deletedId;
    }
}
