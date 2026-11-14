package com.scout.springserver.exception;

/** Discovering POIs / computing routes on a space that isn't geocoded yet → 422 { error: 'Missing coordinates', message }. */
public class MissingCoordinatesException extends RuntimeException {
    public MissingCoordinatesException(String message) {
        super(message);
    }
}
