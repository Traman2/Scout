package com.scout.springserver.model;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.scout.springserver.util.Timestamps;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.NotNull;

/**
 * Columns shared by `routes` and `university_commutes`: a walk + transit trip
 * split into per-mode buckets. durationSeconds is always the sum of every
 * bucket, including waitDurationSeconds.
 */
@MappedSuperclass
public abstract class TripDurations {
    @NotNull
    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @NotNull
    @Column(name = "walk_duration_seconds", nullable = false)
    private Integer walkDurationSeconds;

    @NotNull
    @Column(name = "metro_duration_seconds", nullable = false)
    private Integer metroDurationSeconds;

    @NotNull
    @Column(name = "bus_duration_seconds", nullable = false)
    private Integer busDurationSeconds;

    @NotNull
    @Column(name = "tram_duration_seconds", nullable = false)
    private Integer tramDurationSeconds;

    @NotNull
    @Column(name = "other_duration_seconds", nullable = false)
    private Integer otherDurationSeconds;

    @NotNull
    @Column(name = "wait_duration_seconds", nullable = false)
    private Integer waitDurationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_geometry", columnDefinition = "jsonb")
    private List<RouteGeometryLeg> routeGeometry;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @PrePersist
    void onCreate() {
        if (computedAt == null) computedAt = Timestamps.nowUtc();
    }

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

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }
}
