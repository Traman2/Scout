package com.scout.springserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

/**
 * Table `space_pois` (backend/src/models/space-poi.model.ts) — many-to-many
 * link of which shared POIs are near which space. Unique on (space_id, poi_id).
 */
public record SpacePoi(
        int id,
        int spaceId,
        int poiId,
        @JsonProperty("created_at") Instant createdAt) {

    public static final RowMapper<SpacePoi> ROW_MAPPER = (rs, i) -> new SpacePoi(
            rs.getInt("id"),
            rs.getInt("space_id"),
            rs.getInt("poi_id"),
            Timestamps.read(rs, "created_at"));
}
