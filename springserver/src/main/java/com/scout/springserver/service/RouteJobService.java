package com.scout.springserver.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.RouteProgressResponseDTO;
import com.scout.springserver.exception.MissingCoordinatesException;
import com.scout.springserver.exception.NoPoisException;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Route;
import com.scout.springserver.model.Space;
import com.scout.springserver.service.AnalyticsService.Event;
import com.scout.springserver.util.Errors;
import com.scout.springserver.util.Timestamps;

/**
 * POST /api/spaces/:id/route — starts the batched OTP routing job, which
 * outlives the 202 response (the `void computeRoutesForSpace(...).then().catch()`
 * chain in space-route.controller.ts). Durable progress stays the source of
 * truth; terminal analytics are emitted only after that state is written.
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
    private final AnalyticsService analyticsService;
    private final ExecutorService executor;

    public RouteJobService(
            SpaceService spaceService,
            PoiService poiService,
            RoutingService routingService,
            RouteProgressService routeProgressService,
            AnalyticsService analyticsService,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.spaceService = spaceService;
        this.poiService = poiService;
        this.routingService = routingService;
        this.routeProgressService = routeProgressService;
        this.analyticsService = analyticsService;
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
        String jobStartedAt = progress.getStartedAt() != null ? progress.getStartedAt() : Timestamps.toIso(Instant.now());

        analyticsService.capture(ownerId, Event.route_computation_started, Map.of(
            "space_id", space.getId(),
            "poi_count", pois.size(),
            "job_started_at", jobStartedAt));

        executor.execute(() -> run(ownerId, space, pois, jobStartedAt));

        return new StartResult(false, progress);
    }

    private void run(Integer ownerId, Space space, List<Poi> pois, String jobStartedAt) {
        AtomicInteger completedCount = new AtomicInteger();

        try {
            List<Route> routes = routingService.computeRoutesForSpace(space, pois, (completed, total) -> {
                completedCount.set(completed);
                routeProgressService.updateRouteProgress(space.getId(), completed, total);
            });

            RouteProgressResponseDTO terminal = routeProgressService.completeRouteProgress(space.getId());
            if (terminal == null || terminal.getFinishedAt() == null) return;

            analyticsService.capture(ownerId, Event.route_computation_completed, Map.of(
                "space_id", space.getId(),
                "poi_count", pois.size(),
                "route_count", routes.size(),
                "duration_ms", Timestamps.millisBetween(jobStartedAt, terminal.getFinishedAt())));
        } catch (RuntimeException error) {
            log.error("Compute routes error", error);
            RouteProgressResponseDTO terminal =
                routeProgressService.failRouteProgress(space.getId(), Errors.message(error));
            if (terminal == null || terminal.getFinishedAt() == null) return;

            analyticsService.capture(ownerId, Event.route_computation_failed, Map.of(
                "space_id", space.getId(),
                "poi_count", pois.size(),
                "completed_count", completedCount.get(),
                "duration_ms", Timestamps.millisBetween(jobStartedAt, terminal.getFinishedAt()),
                "error_code", AnalyticsService.errorCode(error)));
        }
    }
}
