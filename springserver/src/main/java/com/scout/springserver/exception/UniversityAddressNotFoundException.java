package com.scout.springserver.exception;

/** Nominatim found no match for the university address → 422. */
public class UniversityAddressNotFoundException extends RuntimeException {
    public UniversityAddressNotFoundException(String message) {
        super(message);
    }

    public UniversityAddressNotFoundException() {
        super("University address not found");
    }
}
