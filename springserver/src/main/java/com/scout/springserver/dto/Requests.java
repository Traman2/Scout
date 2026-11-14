package com.scout.springserver.dto;

/** Validated request bodies — the output types of the schemas in the validation package. */
public final class Requests {

    private Requests() {}

    public record SignUp(String name, String email, String password) {}

    public record SignIn(String email, String password) {}

    public record CreateSpace(String name, String address) {}

    /** Every field optional (zod .partial()); null means "not sent". At least one is non-null. */
    public record UpdateSpace(String name, String address, Double lat, Double lng) {}

    /** radiusMeters is null when not sent. */
    public record DiscoverPois(Integer radiusMeters) {}

    public record UpsertUniversity(String name, String address) {}
}
