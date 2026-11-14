package com.scout.springserver.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The tables use `timestamp` (without time zone) columns that Drizzle reads
 * and writes as UTC. These helpers keep the Spring side consistent with that
 * and format timestamps exactly like JSON.stringify(new Date()) in Node.
 */
public final class Timestamps {

    private static final DateTimeFormatter JS_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Timestamps() {}

    /** Same output as JavaScript's Date.prototype.toISOString(). */
    public static String toIso(Instant instant) {
        return instant == null ? null : JS_ISO.format(instant);
    }

    /** A UTC `timestamp` column value → "2025-01-01T12:00:00.000Z". */
    public static String toIso(LocalDateTime utc) {
        return utc == null ? null : JS_ISO.format(utc.toInstant(ZoneOffset.UTC));
    }

    public static LocalDateTime read(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    /** Value for a `timestamp` column, equivalent to drizzle's `new Date()`. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
