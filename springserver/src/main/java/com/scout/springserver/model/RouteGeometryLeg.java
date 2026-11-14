package com.scout.springserver.model;

/**
 * One leg of a stored itinerary in the jsonb `route_geometry` column
 * (SpaceRouteGeometryLeg / UniversityRouteGeometryLeg in the Express models).
 */
public record RouteGeometryLeg(String mode, String encodedPolyline) {}
