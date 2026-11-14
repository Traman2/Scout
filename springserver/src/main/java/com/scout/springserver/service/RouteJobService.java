package com.scout.springserver.service;

import java.util.List;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.RouteProgressResponseDTO;
import com.scout.springserver.exception.MissingCoordinatesException;
import com.scout.springserver.exception.NoPoisException;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Space;
import com.scout.springserver.util.Errors;

/**
 * POST /api/spaces/:id/route — starts the batched OTP routing job, which
 * outlives the 202 response (the `void computeRoutesForSpace(...).then().catch()`
 * chain in space-route.controller.ts). The route_progress row is the source
 * of truth for how the job is going.
 */
@Service
public class RouteJobService {
    private static final Logger log = LoggerFactory.getLogger(RouteJobService.class);

    /** alreadyRunning → 200 "already in progress"; otherwise → 202 "started". */
    public record StartResult(boolean alreadyRunning, RouteProgressResponseDTO progress) {}

    private final SpaceService spaceService;
    private final PoiService poiService;
    private final RoutingService routingService;
    private final RouteProgressService routeProgressService;
    private final ExecutorService executor;

    public RouteJobService(
            SpaceService spaceService,
            PoiService poiService,
            RoutingService routingService,
            RouteProgressService routeProgressService,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.spaceService = spaceService;
        this.poiService = poiService;
        this.routingService = routingService;
        this.routeProgressService = routeProgressService;
        this.executor = executor;
    }

    public StartResult startRouteComputation(Integer spaceId, Integer ownerId) {
        Space space = spaceService.getOwnedSpace(spaceId, ownerId);

        if (!space.hasCoordinates()) {
            throw new MissingCoordinatesException("Geocode this space before computing routes");
        }

        List<Poi> pois = poiService.getPoiEntitiesForSpace(space.getId());
        if (pois.isEmpty()) throw new NoPoisException();

        RouteProgressResponseDTO existing = routeProgressService.getRouteProgress(space.getId());
        if ("in_progress".equals(existing.getStatus())) {
            return new StartResult(true, existing);
        }

        RouteProgressResponseDTO progress = routeProgressService.startRouteProgress(space.getId(), pois.size());
        executor.execute(() -> run(space, pois));
        return new StartResult(false, progress);
    }

    private void run(Space space, List<Poi> pois) {
        try {
            routingService.computeRoutesForSpace(space, pois, (completed, total) ->
                routeProgressService.updateRouteProgress(space.getId(), completed, total));
            routeProgressService.completeRouteProgress(space.getId());
        } catch (RuntimeException error) {
            log.error("Compute routes error", error);
            routeProgressService.failRouteProgress(space.getId(), Errors.message(error));
        }
    }
}
