package com.scout.springserver.service;

import com.scout.springserver.client.Coordinates;

/**
 * Result of the automatic geocode on space create/update. A failure never
 * blocks the save — the space is stored with null coordinates and can be
 * retried with POST /api/spaces/:id/geocode.
 */
public sealed interface GeocodeAttempt {

    record Succeeded(Coordinates coordinates) implements GeocodeAttempt {}

    record Failed(Throwable error) implements GeocodeAttempt {}
}
