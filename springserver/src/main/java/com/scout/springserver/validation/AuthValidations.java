package com.scout.springserver.validation;

import com.scout.springserver.dto.Requests;
import java.util.Map;

/** backend/src/validations/auth.validations.ts */
public final class AuthValidations {

    private AuthValidations() {}

    /** signupSchema */
    public static Requests.SignUp signup(Map<String, Object> body) {
        BodyReader r = new BodyReader(body);
        String name = r.string("name", 2, 255, true);
        String email = r.email("email", 255);
        String password = r.string("password", 6, 128, false);
        r.throwIfInvalid();
        return new Requests.SignUp(name, email, password);
    }

    /** signInSchema */
    public static Requests.SignIn signIn(Map<String, Object> body) {
        BodyReader r = new BodyReader(body);
        String email = r.email("email", Integer.MAX_VALUE);
        String password = r.string("password", 1, Integer.MAX_VALUE, false);
        r.throwIfInvalid();
        return new Requests.SignIn(email, password);
    }
}
