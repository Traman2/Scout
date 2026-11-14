package com.scout.springserver.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Helpers for multi-row `INSERT ... VALUES (...), (...)` statements (drizzle's `.values([...])`). */
public final class SqlValues {

    private SqlValues() {}

    /** "(?, ?, ?), (?, ?, ?)" for `rows` rows built from a single-row tuple like "(?, ?, ?)". */
    public static String tuples(String rowTuple, int rows) {
        return String.join(", ", Collections.nCopies(rows, rowTuple));
    }

    /** Splits a list so each statement stays well under Postgres' 65535 bind-parameter limit. */
    public static <T> List<List<T>> chunks(List<T> items, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            chunks.add(items.subList(i, Math.min(items.size(), i + size)));
        }
        return chunks;
    }
}
