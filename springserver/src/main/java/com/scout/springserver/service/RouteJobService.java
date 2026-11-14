package com.scout.springserver.service;

import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.RouteProgressSnapshot;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Route;
import com.scout.springserver.model.Space;
import com.scout.springserver.service.AnalyticsService.Event;
import com.scout.springserver.util.Errors;
import com.scout.springserver.util.Timestamps;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * The background part of POST /api/spaces/:id/route — the job that outlives
 * the 202 response (the `void computeRoutesForSpace(...).then().catch()` chain
 * in space-route.controller.ts). Durable progress stays the source of truth;
 * terminal analytics are emitted only after that state is written.
 */
@Service
public class RouteJobService {

    private static final Logger log = LoggerFactory.getLogger(RouteJobService.class);

    private final RoutingService routingService;
    private final RouteProgressService progressService;
    private final AnalyticsService analytics;
    private final ExecutorService executor;

    public RouteJobService(
            RoutingService routingService,
            RouteProgressService progressService,
            AnalyticsService analytics,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.routingService = routingService;
        this.progressService = progressService;
        this.analytics = analytics;
        this.executor = executor;
    }

    public void runInBackground(int ownerId, Space space, List<Poi> pois, String jobStartedAt) {
        executor.execute(() -> run(ownerId, space, pois, jobStartedAt));
    }

    private void run(int ownerId, Space space, List<Poi> pois, String jobStartedAt) {
        AtomicInteger completedCount = new AtomicInteger();

        try {
            List<Route> routes = routingService.computeRoutesForSpace(
                    space.id(), space.lat(), space.lng(), pois,
                    (completed, total) -> {
                        completedCount.set(completed);
                        progressService.updateRouteProgress(space.id(), completed, total);
                    });

            RouteProgressSnapshot terminal = progressService.completeRouteProgress(space.id());
            if (terminal == null || terminal.finishedAt() == null) return;

            analytics.capture(ownerId, Event.route_computation_completed, Map.of(
                    "space_id", space.id(),
                    "poi_count", pois.size(),
                    "route_count", routes.size(),
                    "duration_ms", Timestamps.millisBetween(jobStartedAt, terminal.finishedAt())));
        } catch (RuntimeException error) {
            log.error("Compute routes error", error);
            RouteProgressSnapshot terminal = progressService.failRouteProgress(space.id(), Errors.message(error));
            if (terminal == null || terminal.finishedAt() == null) return;

            analytics.capture(ownerId, Event.route_computation_failed, Map.of(
                    "space_id", space.id(),
                    "poi_count", pois.size(),
                    "completed_count", completedCount.get(),
                    "duration_ms", Timestamps.millisBetween(jobStartedAt, terminal.finishedAt()),
                    "error_code", AnalyticsService.errorCode(error)));
        }
    }
}
