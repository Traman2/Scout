package com.scout.springserver.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The tables use `timestamp` (without time zone) columns that Drizzle reads
 * and writes as UTC. These helpers keep the Spring side consistent with that.
 */
public final class Timestamps {

    private static final DateTimeFormatter JS_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Timestamps() {}

    /** Same output as JavaScript's Date.prototype.toISOString(). */
    public static String toIso(Instant instant) {
        return instant == null ? null : JS_ISO.format(instant);
    }

    public static Instant read(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /** Value to bind for a `timestamp` column, equivalent to drizzle's `new Date()`. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Milliseconds between two toIso() strings, never negative (durationBetween in space-route.controller.ts). */
    public static long millisBetween(String startedAt, String finishedAt) {
        try {
            long duration = Duration.between(Instant.parse(startedAt), Instant.parse(finishedAt)).toMillis();
            return Math.max(0, duration);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
