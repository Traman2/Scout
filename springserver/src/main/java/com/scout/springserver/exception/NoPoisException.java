package com.scout.springserver.exception;

/** Computing routes before discovering POIs → 422 { error: 'No POIs', ... }. */
public class NoPoisException extends RuntimeException {
    public NoPoisException(String message) {
        super(message);
    }

    public NoPoisException() {
        super("Discover POIs before computing routes");
    }
}
