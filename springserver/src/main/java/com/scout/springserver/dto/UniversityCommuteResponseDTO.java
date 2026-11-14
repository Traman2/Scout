package com.scout.springserver.dto;

/** One commute in the university dashboard — a university_commutes row joined with its space. */
public class UniversityCommuteResponseDTO extends TripDurationsDTO {
    private Integer id;
    private Integer spaceId;
    private String spaceName;
    private String spaceAddress;
    private Double spaceLat;
    private Double spaceLng;

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

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public String getSpaceAddress() {
        return spaceAddress;
    }

    public void setSpaceAddress(String spaceAddress) {
        this.spaceAddress = spaceAddress;
    }

    public Double getSpaceLat() {
        return spaceLat;
    }

    public void setSpaceLat(Double spaceLat) {
        this.spaceLat = spaceLat;
    }

    public Double getSpaceLng() {
        return spaceLng;
    }

    public void setSpaceLng(Double spaceLng) {
        this.spaceLng = spaceLng;
    }
}
