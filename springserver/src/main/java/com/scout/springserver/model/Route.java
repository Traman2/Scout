package com.scout.springserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.util.JsonColumns;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

/**
 * Table `routes` (backend/src/models/route.model.ts). Per-space (not shared):
 * the walk+transit trip from one POI to one space. Unique on (space_id, poi_id).
 * durationSeconds is always the sum of every *DurationSeconds bucket.
 */
public record Route(
        int id,
        int spaceId,
        int poiId,
        int durationSeconds,
        int walkDurationSeconds,
        int metroDurationSeconds,
        int busDurationSeconds,
        int tramDurationSeconds,
        int otherDurationSeconds,
        int waitDurationSeconds,
        List<RouteGeometryLeg> routeGeometry,
        @JsonProperty("computed_at") Instant computedAt) {

    public static final RowMapper<Route> ROW_MAPPER = (rs, i) -> new Route(
            rs.getInt("id"),
            rs.getInt("space_id"),
            rs.getInt("poi_id"),
            rs.getInt("duration_seconds"),
            rs.getInt("walk_duration_seconds"),
            rs.getInt("metro_duration_seconds"),
            rs.getInt("bus_duration_seconds"),
            rs.getInt("tram_duration_seconds"),
            rs.getInt("other_duration_seconds"),
            rs.getInt("wait_duration_seconds"),
            JsonColumns.readLegs(rs.getString("route_geometry")),
            Timestamps.read(rs, "computed_at"));
}
