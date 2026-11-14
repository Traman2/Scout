package com.scout.springserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

/**
 * Table `routes` (backend/src/models/route.model.ts). Per-space (not shared):
 * the walk + transit trip from one POI to one space.
 */
@Entity
@Table(name = "routes", uniqueConstraints = @UniqueConstraint(columnNames = {"space_id", "poi_id"}))
public class Route extends TripDurations {
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
}
