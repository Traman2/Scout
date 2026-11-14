package com.scout.springserver.exception;

/** GET /me for a user that no longer exists → 401 { error: 'Unauthorized', message: 'User not found' }. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException() {
        super("User not found");
    }
}
