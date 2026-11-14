package com.scout.springserver.client;

/** A lat/lng pair (GeocodeResult in nominatim.client.ts). */
public record Coordinates(double lat, double lng) {}
