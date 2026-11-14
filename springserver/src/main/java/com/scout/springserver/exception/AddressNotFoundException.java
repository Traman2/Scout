package com.scout.springserver.exception;

/** Nominatim found no match for a space address → 422 { error: 'Address not found', ... }. */
public class AddressNotFoundException extends RuntimeException {
    public AddressNotFoundException(String message) {
        super(message);
    }

    public AddressNotFoundException() {
        super("Address not found");
    }
}
