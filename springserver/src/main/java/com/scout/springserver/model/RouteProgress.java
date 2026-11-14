package com.scout.springserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

/**
 * Table `route_progress` (backend/src/models/route-progress.model.ts). One row
 * per space, upserted on every route computation so the frontend can reattach
 * to an in-flight or just-finished job. status: 'in_progress' | 'done' | 'error'.
 */
public record RouteProgress(
        int id,
        int spaceId,
        String status,
        int completed,
        int total,
        String error,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("finished_at") Instant finishedAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static final RowMapper<RouteProgress> ROW_MAPPER = (rs, i) -> new RouteProgress(
            rs.getInt("id"),
            rs.getInt("space_id"),
            rs.getString("status"),
            rs.getInt("completed"),
            rs.getInt("total"),
            rs.getString("error"),
            Timestamps.read(rs, "started_at"),
            Timestamps.read(rs, "finished_at"),
            Timestamps.read(rs, "updated_at"));
}
