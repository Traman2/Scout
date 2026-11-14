package com.scout.springserver.exception;

/** Computing commutes before saving a university → 422. */
public class UniversityNotConfiguredException extends RuntimeException {
    public UniversityNotConfiguredException(String message) {
        super(message);
    }

    public UniversityNotConfiguredException() {
        super("University not configured");
    }
}
