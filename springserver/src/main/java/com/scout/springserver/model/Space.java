package com.scout.springserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

/** Table `spaces` (backend/src/models/space.model.ts). lat/lng stay null until geocoded. */
public record Space(
        int id,
        int ownerId,
        String name,
        String address,
        Double lat,
        Double lng,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static final RowMapper<Space> ROW_MAPPER = (rs, i) -> new Space(
            rs.getInt("id"),
            rs.getInt("owner_id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getObject("lat", Double.class),
            rs.getObject("lng", Double.class),
            Timestamps.read(rs, "created_at"),
            Timestamps.read(rs, "updated_at"));

    public boolean hasCoordinates() {
        return lat != null && lng != null;
    }
}
