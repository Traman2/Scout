package com.scout.springserver.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the small `{ message, space }`-style JSON bodies the controllers
 * return. Unlike Map.of, keeps insertion order and allows null values
 * (e.g. `university: null`).
 */
public final class Bodies {

    private Bodies() {}

    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Bodies.of needs key/value pairs");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            body.put((String) keyValues[i], keyValues[i + 1]);
        }
        return body;
    }
}
