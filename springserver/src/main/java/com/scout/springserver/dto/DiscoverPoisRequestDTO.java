package com.scout.springserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** POST /api/spaces/:id/discover — discoverPoisSchema. The whole body is optional. */
public class DiscoverPoisRequestDTO {
    @Min(value = 100, message = "radiusMeters must be >= 100")
    @Max(value = 5000, message = "radiusMeters must be <= 5000")
    private Integer radiusMeters;

    public Integer getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Integer radiusMeters) {
        this.radiusMeters = radiusMeters;
    }
}
