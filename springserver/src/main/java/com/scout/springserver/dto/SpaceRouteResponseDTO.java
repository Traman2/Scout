package com.scout.springserver.dto;

/** One row of GET /api/spaces/:id/routes — a route joined with its POI's name and category. */
public class SpaceRouteResponseDTO extends TripDurationsDTO {
    private Integer id;
    private Integer poiId;
    private String poiName;
    private String poiCategory;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getPoiId() {
        return poiId;
    }

    public void setPoiId(Integer poiId) {
        this.poiId = poiId;
    }

    public String getPoiName() {
        return poiName;
    }

    public void setPoiName(String poiName) {
        this.poiName = poiName;
    }

    public String getPoiCategory() {
        return poiCategory;
    }

    public void setPoiCategory(String poiCategory) {
        this.poiCategory = poiCategory;
    }
}
