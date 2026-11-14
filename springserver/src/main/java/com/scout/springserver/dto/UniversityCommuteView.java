package com.scout.springserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.model.RouteGeometryLeg;
import com.scout.springserver.util.JsonColumns;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

/** One commute in the university dashboard — a university_commutes row joined with its space. */
public record UniversityCommuteView(
        int id,
        int spaceId,
        String spaceName,
        String spaceAddress,
        Double spaceLat,
        Double spaceLng,
        int durationSeconds,
        int walkDurationSeconds,
        int metroDurationSeconds,
        int busDurationSeconds,
        int tramDurationSeconds,
        int otherDurationSeconds,
        int waitDurationSeconds,
        List<RouteGeometryLeg> routeGeometry,
        @JsonProperty("computed_at") Instant computedAt) {

    public static final RowMapper<UniversityCommuteView> ROW_MAPPER = (rs, i) -> new UniversityCommuteView(
            rs.getInt("id"),
            rs.getInt("space_id"),
            rs.getString("space_name"),
            rs.getString("space_address"),
            rs.getObject("space_lat", Double.class),
            rs.getObject("space_lng", Double.class),
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
