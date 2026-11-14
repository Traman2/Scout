package com.scout.springserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

/**
 * Table `pois` (backend/src/models/poi.model.ts). Shared across all spaces —
 * the same OSM node is stored once (unique osm_id) and linked to every nearby
 * space through `space_pois`.
 */
public record Poi(
        int id,
        long osmId,
        String category,
        String name,
        double lat,
        double lng,
        @JsonProperty("created_at") Instant createdAt) {

    public static final RowMapper<Poi> ROW_MAPPER = (rs, i) -> new Poi(
            rs.getInt("id"),
            rs.getLong("osm_id"),
            rs.getString("category"),
            rs.getString("name"),
            rs.getDouble("lat"),
            rs.getDouble("lng"),
            Timestamps.read(rs, "created_at"));
}
