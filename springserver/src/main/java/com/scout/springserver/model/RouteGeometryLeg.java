package com.scout.springserver.model;

/**
 * One leg of a stored itinerary, as stored in the jsonb `route_geometry`
 * column (SpaceRouteGeometryLeg / UniversityRouteGeometryLeg in the Express models).
 */
public class RouteGeometryLeg {
    private String mode;
    private String encodedPolyline;

    public RouteGeometryLeg() {}

    public RouteGeometryLeg(String mode, String encodedPolyline) {
        this.mode = mode;
        this.encodedPolyline = encodedPolyline;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getEncodedPolyline() {
        return encodedPolyline;
    }

    public void setEncodedPolyline(String encodedPolyline) {
        this.encodedPolyline = encodedPolyline;
    }
}
