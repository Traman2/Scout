package com.scout.springserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

/** Table `universities` (backend/src/models/university.model.ts). One per user (unique owner_id). */
public record University(
        int id,
        int ownerId,
        String name,
        String address,
        double lat,
        double lng,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static final RowMapper<University> ROW_MAPPER = (rs, i) -> new University(
            rs.getInt("id"),
            rs.getInt("owner_id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getDouble("lat"),
            rs.getDouble("lng"),
            Timestamps.read(rs, "created_at"),
            Timestamps.read(rs, "updated_at"));
}
