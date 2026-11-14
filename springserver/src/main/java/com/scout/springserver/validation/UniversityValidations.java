package com.scout.springserver.validation;

import com.scout.springserver.dto.Requests;
import java.util.Map;

/** backend/src/validations/university.validations.ts */
public final class UniversityValidations {

    private UniversityValidations() {}

    /** upsertUniversitySchema */
    public static Requests.UpsertUniversity upsertUniversity(Map<String, Object> body) {
        BodyReader r = new BodyReader(body);
        String name = r.string("name", 1, 255, true);
        String address = r.string("address", 1, 1000, true);
        r.throwIfInvalid();
        return new Requests.UpsertUniversity(name, address);
    }
}
