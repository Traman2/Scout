package com.scout.springserver.service;

import com.scout.springserver.dto.RouteProgressSnapshot;
import com.scout.springserver.model.RouteProgress;
import com.scout.springserver.util.Timestamps;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * backend/src/services/route-progress.service.ts. The route_progress row is
 * the durable source of truth; every write is also published as a
 * RouteProgressEvent for live Socket.IO fan-out.
 */
@Service
public class RouteProgressService {

    private static final Logger log = LoggerFactory.getLogger(RouteProgressService.class);

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    public RouteProgressService(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    private void emitProgress(int spaceId, RouteProgressSnapshot snapshot) {
        events.publishEvent(new RouteProgressEvent(spaceId, snapshot));
    }

    public RouteProgressSnapshot getRouteProgress(int spaceId) {
        return jdbc.sql("SELECT * FROM route_progress WHERE space_id = ? LIMIT 1")
                .param(spaceId)
                .query(RouteProgress.ROW_MAPPER)
                .optional()
                .map(RouteProgressSnapshot::from)
                .orElseGet(RouteProgressSnapshot::idle);
    }

    public RouteProgressSnapshot startRouteProgress(int spaceId, int total) {
        LocalDateTime now = Timestamps.nowUtc();
        RouteProgress row = jdbc.sql("""
                        INSERT INTO route_progress
                            (space_id, status, completed, total, error, started_at, finished_at, updated_at)
                        VALUES (:spaceId, 'in_progress', 0, :total, NULL, :now, NULL, :now)
                        ON CONFLICT (space_id) DO UPDATE SET
                            status = 'in_progress',
                            completed = 0,
                            total = :total,
                            error = NULL,
                            started_at = :now,
                            finished_at = NULL,
                            updated_at = :now
                        RETURNING *
                        """)
                .param("spaceId", spaceId)
                .param("total", total)
                .param("now", now)
                .query(RouteProgress.ROW_MAPPER)
                .single();

        RouteProgressSnapshot snapshot = RouteProgressSnapshot.from(row);
        emitProgress(spaceId, snapshot);
        return snapshot;
    }

    /** Never throws — a failed progress write shouldn't fail the routing job. */
    public void updateRouteProgress(int spaceId, int completed, int total) {
        try {
            jdbc.sql("""
                            UPDATE route_progress SET completed = :completed, total = :total, updated_at = :now
                            WHERE space_id = :spaceId
                            RETURNING *
                            """)
                    .param("completed", completed)
                    .param("total", total)
                    .param("now", Timestamps.nowUtc())
                    .param("spaceId", spaceId)
                    .query(RouteProgress.ROW_MAPPER)
                    .optional()
                    .ifPresent(row -> emitProgress(spaceId, RouteProgressSnapshot.from(row)));
        } catch (RuntimeException e) {
            log.error("Error updating route progress", e);
        }
    }

    /** Null if there's no row or the write failed. */
    public RouteProgressSnapshot completeRouteProgress(int spaceId) {
        try {
            LocalDateTime now = Timestamps.nowUtc();
            Optional<RouteProgress> row = jdbc.sql("""
                            UPDATE route_progress SET status = 'done', finished_at = :now, updated_at = :now
                            WHERE space_id = :spaceId
                            RETURNING *
                            """)
                    .param("now", now)
                    .param("spaceId", spaceId)
                    .query(RouteProgress.ROW_MAPPER)
                    .optional();
            return row.map(r -> emitAndReturn(spaceId, r)).orElse(null);
        } catch (RuntimeException e) {
            log.error("Error completing route progress", e);
            return null;
        }
    }

    /** Null if there's no row or the write failed. */
    public RouteProgressSnapshot failRouteProgress(int spaceId, String error) {
        try {
            LocalDateTime now = Timestamps.nowUtc();
            Optional<RouteProgress> row = jdbc.sql("""
                            UPDATE route_progress
                            SET status = 'error', error = :error, finished_at = :now, updated_at = :now
                            WHERE space_id = :spaceId
                            RETURNING *
                            """)
                    .param("error", error)
                    .param("now", now)
                    .param("spaceId", spaceId)
                    .query(RouteProgress.ROW_MAPPER)
                    .optional();
            return row.map(r -> emitAndReturn(spaceId, r)).orElse(null);
        } catch (RuntimeException e) {
            log.error("Error failing route progress", e);
            return null;
        }
    }

    private RouteProgressSnapshot emitAndReturn(int spaceId, RouteProgress row) {
        RouteProgressSnapshot snapshot = RouteProgressSnapshot.from(row);
        emitProgress(spaceId, snapshot);
        return snapshot;
    }
}
