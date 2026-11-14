package com.scout.springserver.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.model.RouteGeometryLeg;

/** Duration buckets + geometry shared by route and commute responses. */
public abstract class TripDurationsDTO {
    private Integer durationSeconds;
    private Integer walkDurationSeconds;
    private Integer metroDurationSeconds;
    private Integer busDurationSeconds;
    private Integer tramDurationSeconds;
    private Integer otherDurationSeconds;
    private Integer waitDurationSeconds;
    private List<RouteGeometryLeg> routeGeometry;

    @JsonProperty("computed_at")
    private String computedAt;

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getWalkDurationSeconds() {
        return walkDurationSeconds;
    }

    public void setWalkDurationSeconds(Integer walkDurationSeconds) {
        this.walkDurationSeconds = walkDurationSeconds;
    }

    public Integer getMetroDurationSeconds() {
        return metroDurationSeconds;
    }

    public void setMetroDurationSeconds(Integer metroDurationSeconds) {
        this.metroDurationSeconds = metroDurationSeconds;
    }

    public Integer getBusDurationSeconds() {
        return busDurationSeconds;
    }

    public void setBusDurationSeconds(Integer busDurationSeconds) {
        this.busDurationSeconds = busDurationSeconds;
    }

    public Integer getTramDurationSeconds() {
        return tramDurationSeconds;
    }

    public void setTramDurationSeconds(Integer tramDurationSeconds) {
        this.tramDurationSeconds = tramDurationSeconds;
    }

    public Integer getOtherDurationSeconds() {
        return otherDurationSeconds;
    }

    public void setOtherDurationSeconds(Integer otherDurationSeconds) {
        this.otherDurationSeconds = otherDurationSeconds;
    }

    public Integer getWaitDurationSeconds() {
        return waitDurationSeconds;
    }

    public void setWaitDurationSeconds(Integer waitDurationSeconds) {
        this.waitDurationSeconds = waitDurationSeconds;
    }

    public List<RouteGeometryLeg> getRouteGeometry() {
        return routeGeometry;
    }

    public void setRouteGeometry(List<RouteGeometryLeg> routeGeometry) {
        this.routeGeometry = routeGeometry;
    }

    public String getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(String computedAt) {
        this.computedAt = computedAt;
    }
}
