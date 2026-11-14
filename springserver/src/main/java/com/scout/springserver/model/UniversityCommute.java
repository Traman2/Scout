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
 * Table `university_commutes` (backend/src/models/university.model.ts) — the
 * trip from one space to the user's university.
 */
@Entity
@Table(name = "university_commutes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"university_id", "space_id"}))
public class UniversityCommute extends TripDurations {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** FK → universities.id (on delete cascade) */
    @NotNull
    @Column(name = "university_id", nullable = false)
    private Integer universityId;

    /** FK → spaces.id (on delete cascade) */
    @NotNull
    @Column(name = "space_id", nullable = false)
    private Integer spaceId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUniversityId() {
        return universityId;
    }

    public void setUniversityId(Integer universityId) {
        this.universityId = universityId;
    }

    public Integer getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Integer spaceId) {
        this.spaceId = spaceId;
    }
}
