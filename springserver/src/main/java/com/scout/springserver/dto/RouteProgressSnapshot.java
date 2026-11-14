package com.scout.springserver.dto;

import com.scout.springserver.model.RouteProgress;
import com.scout.springserver.util.Timestamps;

/**
 * What the API and the `route-progress` socket event send (RouteProgressSnapshot
 * in route-progress.service.ts). status: 'idle' | 'in_progress' | 'done' | 'error'.
 * Timestamps are pre-formatted strings so the Socket.IO serializer emits the
 * same shape as the REST endpoint.
 */
public record RouteProgressSnapshot(
        String status,
        int completed,
        int total,
        String error,
        String startedAt,
        String finishedAt) {

    public static RouteProgressSnapshot idle() {
        return new RouteProgressSnapshot("idle", 0, 0, null, null, null);
    }

    public static RouteProgressSnapshot from(RouteProgress row) {
        return new RouteProgressSnapshot(
                row.status(),
                row.completed(),
                row.total(),
                row.error(),
                Timestamps.toIso(row.startedAt()),
                Timestamps.toIso(row.finishedAt()));
    }
}
