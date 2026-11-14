package com.scout.springserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/spaces/:id — updateSpaceSchema in space.validations.ts.
 * Every field is optional (null = not sent), but at least one must be sent.
 * Explicit lat/lng always win over the geocode triggered by a new address.
 */
public class UpdateSpaceRequestDTO {
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;

    @Size(min = 1, max = 1000, message = "Address must be between 1 and 1000 characters")
    private String address;

    @DecimalMin(value = "-90", message = "Latitude must be >= -90")
    @DecimalMax(value = "90", message = "Latitude must be <= 90")
    private Double lat;

    @DecimalMin(value = "-180", message = "Longitude must be >= -180")
    @DecimalMax(value = "180", message = "Longitude must be <= 180")
    private Double lng;

    @JsonIgnore
    @AssertTrue(message = "At least one field must be provided")
    public boolean isAnyFieldProvided() {
        return name != null || address != null || lat != null || lng != null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address == null ? null : address.trim();
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }
}
