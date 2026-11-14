package com.scout.springserver.service;

import com.scout.springserver.dto.RouteProgressSnapshot;

/**
 * Published on every route_progress write — the Spring equivalent of
 * routeProgressEvents.emit('progress', spaceId, snapshot). The Socket.IO
 * server listens for it and fans it out to that space's room.
 */
public record RouteProgressEvent(int spaceId, RouteProgressSnapshot snapshot) {}
