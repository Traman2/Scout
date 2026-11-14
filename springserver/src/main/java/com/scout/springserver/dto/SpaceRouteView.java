package com.scout.springserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.model.RouteGeometryLeg;
import com.scout.springserver.util.JsonColumns;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

/** One row of GET /api/spaces/:id/routes — a route joined with its POI's name and category. */
public record SpaceRouteView(
        int id,
        int poiId,
        String poiName,
        String poiCategory,
        int durationSeconds,
        int walkDurationSeconds,
        int metroDurationSeconds,
        int busDurationSeconds,
        int tramDurationSeconds,
        int otherDurationSeconds,
        int waitDurationSeconds,
        List<RouteGeometryLeg> routeGeometry,
        @JsonProperty("computed_at") Instant computedAt) {

    public static final RowMapper<SpaceRouteView> ROW_MAPPER = (rs, i) -> new SpaceRouteView(
            rs.getInt("id"),
            rs.getInt("poi_id"),
            rs.getString("poi_name"),
            rs.getString("poi_category"),
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
