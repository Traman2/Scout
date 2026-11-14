package com.scout.springserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.util.JsonColumns;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

/**
 * Table `university_commutes` (backend/src/models/university.model.ts) — the
 * trip from one space to the user's university. Unique on (university_id, space_id).
 */
public record UniversityCommute(
        int id,
        int universityId,
        int spaceId,
        int durationSeconds,
        int walkDurationSeconds,
        int metroDurationSeconds,
        int busDurationSeconds,
        int tramDurationSeconds,
        int otherDurationSeconds,
        int waitDurationSeconds,
        List<RouteGeometryLeg> routeGeometry,
        @JsonProperty("computed_at") Instant computedAt) {

    public static final RowMapper<UniversityCommute> ROW_MAPPER = (rs, i) -> new UniversityCommute(
            rs.getInt("id"),
            rs.getInt("university_id"),
            rs.getInt("space_id"),
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
