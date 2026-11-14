package com.scout.springserver.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scout.springserver.model.RouteGeometryLeg;
import java.util.List;

/** Reads/writes the jsonb `route_geometry` columns on routes and university_commutes. */
public final class JsonColumns {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<List<RouteGeometryLeg>> LEGS = new TypeReference<>() {};

    private JsonColumns() {}

    public static List<RouteGeometryLeg> readLegs(String json) {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, LEGS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid route_geometry JSON", e);
        }
    }

    public static String writeLegs(List<RouteGeometryLeg> legs) {
        if (legs == null) return null;
        try {
            return MAPPER.writeValueAsString(legs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize route_geometry", e);
        }
    }
}
