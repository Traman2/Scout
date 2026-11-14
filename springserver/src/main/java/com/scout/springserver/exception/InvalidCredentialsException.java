package com.scout.springserver.exception;

/** Unknown email or wrong password at sign-in → 401 { error: 'Invalid email or password' }. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
