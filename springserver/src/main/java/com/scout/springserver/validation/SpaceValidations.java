package com.scout.springserver.validation;

import com.scout.springserver.dto.Requests;
import com.scout.springserver.exception.DomainExceptions.ValidationException;
import java.util.Map;

/** backend/src/validations/space.validations.ts */
public final class SpaceValidations {

    private SpaceValidations() {}

    /** createSpaceSchema */
    public static Requests.CreateSpace createSpace(Map<String, Object> body) {
        BodyReader r = new BodyReader(body);
        String name = r.string("name", 1, 255, true);
        String address = r.string("address", 1, 1000, true);
        r.throwIfInvalid();
        return new Requests.CreateSpace(name, address);
    }

    /** updateSpaceSchema — every field optional, but at least one must be sent. */
    public static Requests.UpdateSpace updateSpace(Map<String, Object> body) {
        BodyReader r = new BodyReader(body);
        String name = r.has("name") ? r.string("name", 1, 255, true) : null;
        String address = r.has("address") ? r.string("address", 1, 1000, true) : null;
        Double lat = r.has("lat") ? r.number("lat", -90, 90) : null;
        Double lng = r.has("lng") ? r.number("lng", -180, 180) : null;

        boolean anyField = r.has("name") || r.has("address") || r.has("lat") || r.has("lng");
        if (!anyField) r.addError("At least one field must be provided");

        r.throwIfInvalid();
        return new Requests.UpdateSpace(name, address, lat, lng);
    }

    /** spaceIdParamsSchema — z.coerce.number().int().positive() on the :id path param. */
    public static int spaceId(String rawId) {
        double value = BodyReader.coerceNumber(rawId);
        if (!Double.isFinite(value)) {
            throw new ValidationException("id: Invalid input: expected number, received NaN");
        }
        if (value != Math.rint(value) || value > Integer.MAX_VALUE) {
            throw new ValidationException("id: Invalid input: expected int, received number");
        }
        if (value <= 0) {
            throw new ValidationException("id: Too small: expected number to be >0");
        }
        return (int) value;
    }

    /** discoverPoisSchema — radiusMeters optional, int between 100 and 5000. */
    public static Requests.DiscoverPois discoverPois(Map<String, Object> body) {
        BodyReader r = new BodyReader(body);
        Integer radiusMeters = r.has("radiusMeters") ? r.integer("radiusMeters", 100, 5000) : null;
        r.throwIfInvalid();
        return new Requests.DiscoverPois(radiusMeters);
    }
}
