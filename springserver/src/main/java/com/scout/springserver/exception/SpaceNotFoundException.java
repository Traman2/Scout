package com.scout.springserver.exception;

/** No space with that id owned by the caller → 404 { error: 'Space not found' }. */
public class SpaceNotFoundException extends RuntimeException {
    public SpaceNotFoundException(String message) {
        super(message);
    }

    public SpaceNotFoundException() {
        super("Space not found");
    }
}
