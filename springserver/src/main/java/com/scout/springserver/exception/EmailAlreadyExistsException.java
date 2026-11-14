package com.scout.springserver.exception;

/** Sign-up with an email that's taken → 409 { error: 'Email already exists' }. */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public EmailAlreadyExistsException() {
        super("User with this email already exists");
    }
}
