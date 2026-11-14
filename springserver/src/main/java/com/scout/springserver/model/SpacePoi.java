package com.scout.springserver.model;

import java.time.LocalDateTime;

import com.scout.springserver.util.Timestamps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

/**
 * Table `space_pois` (backend/src/models/space-poi.model.ts) — many-to-many
 * link of which shared POIs are near which space.
 */
@Entity
@Table(name = "space_pois", uniqueConstraints = @UniqueConstraint(columnNames = {"space_id", "poi_id"}))
public class SpacePoi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** FK → spaces.id (on delete cascade) */
    @NotNull
    @Column(name = "space_id", nullable = false)
    private Integer spaceId;

    /** FK → pois.id (on delete cascade) */
    @NotNull
    @Column(name = "poi_id", nullable = false)
    private Integer poiId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Timestamps.nowUtc();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Integer spaceId) {
        this.spaceId = spaceId;
    }

    public Integer getPoiId() {
        return poiId;
    }

    public void setPoiId(Integer poiId) {
        this.poiId = poiId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
