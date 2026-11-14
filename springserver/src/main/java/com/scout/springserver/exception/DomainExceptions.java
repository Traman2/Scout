package com.scout.springserver.exception;

/**
 * Errors services throw, one type per `new Error('...')` message the Express
 * controllers check for. GlobalExceptionHandler turns each into the same
 * status code and body the Express controller returned for it.
 */
public final class DomainExceptions {

    private DomainExceptions() {}

    /** Request body/params failed validation → 400 { error: 'Validation failed', details }. */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String details) {
            super(details);
        }
    }

    /** 'User with this email already exists' → 409. */
    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException() {
            super("User with this email already exists");
        }
    }

    /** 'User not found' / 'Invalid password' during sign-in → 401. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    /** 'User not found' from getUserById (GET /me) → 401. */
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException() {
            super("User not found");
        }
    }

    /** 'Space not found' → 404. */
    public static class SpaceNotFoundException extends RuntimeException {
        public SpaceNotFoundException() {
            super("Space not found");
        }
    }

    /** 'Address not found' (space geocoding) → 422. */
    public static class AddressNotFoundException extends RuntimeException {
        public AddressNotFoundException() {
            super("Address not found");
        }
    }

    /** 'University address not found' → 422. */
    public static class UniversityAddressNotFoundException extends RuntimeException {
        public UniversityAddressNotFoundException() {
            super("University address not found");
        }
    }

    /** 'University not configured' → 422. */
    public static class UniversityNotConfiguredException extends RuntimeException {
        public UniversityNotConfiguredException() {
            super("University not configured");
        }
    }

    /** 'No geocoded spaces' → 422. */
    public static class NoGeocodedSpacesException extends RuntimeException {
        public NoGeocodedSpacesException() {
            super("No geocoded spaces");
        }
    }
}
