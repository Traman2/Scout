package com.scout.springserver.exception;

/** Computing commutes with no geocoded spaces → 422. */
public class NoGeocodedSpacesException extends RuntimeException {
    public NoGeocodedSpacesException(String message) {
        super(message);
    }

    public NoGeocodedSpacesException() {
        super("No geocoded spaces");
    }
}
