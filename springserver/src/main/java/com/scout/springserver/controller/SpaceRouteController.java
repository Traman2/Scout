package com.scout.springserver.controller;

import com.scout.springserver.dto.RouteProgressSnapshot;
import com.scout.springserver.exception.ApiException;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Space;
import com.scout.springserver.service.AnalyticsService;
import com.scout.springserver.service.AnalyticsService.Event;
import com.scout.springserver.service.PoiService;
import com.scout.springserver.service.RouteJobService;
import com.scout.springserver.service.RouteProgressService;
import com.scout.springserver.service.RoutingService;
import com.scout.springserver.service.SpaceService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;
import com.scout.springserver.util.Timestamps;
import com.scout.springserver.validation.SpaceValidations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Transit-route routes on a space — backend/src/controllers/space-route.controller.ts */
@RestController
@RequestMapping("/api/spaces")
public class SpaceRouteController {

    private final SpaceService spaceService;
    private final PoiService poiService;
    private final RoutingService routingService;
    private final RouteProgressService progressService;
    private final RouteJobService routeJobService;
    private final AnalyticsService analytics;

    public SpaceRouteController(
            SpaceService spaceService,
            PoiService poiService,
            RoutingService routingService,
            RouteProgressService progressService,
            RouteJobService routeJobService,
            AnalyticsService analytics) {
        this.spaceService = spaceService;
        this.poiService = poiService;
        this.routingService = routingService;
        this.progressService = progressService;
        this.routeJobService = routeJobService;
        this.analytics = analytics;
    }

    /**
     * Starts route computation in the background and answers 202 right away;
     * progress is polled via /route-progress or pushed over Socket.IO.
     */
    @PostMapping("/{id}/route")
    public ResponseEntity<Map<String, Object>> computeRoutes(AuthUser user, @PathVariable("id") String rawId) {
        int spaceId = SpaceValidations.spaceId(rawId);
        Space space = spaceService.getSpaceForUser(spaceId, user.id());

        if (!space.hasCoordinates()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Missing coordinates", "Geocode this space before computing routes");
        }

        List<Poi> pois = poiService.listPoisForSpace(space.id());
        if (pois.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No POIs", "Discover POIs before computing routes");
        }

        RouteProgressSnapshot existing = progressService.getRouteProgress(space.id());
        if ("in_progress".equals(existing.status())) {
            return ResponseEntity.ok(Bodies.of(
                    "message", "Route computation already in progress",
                    "progress", existing));
        }

        RouteProgressSnapshot progress = progressService.startRouteProgress(space.id(), pois.size());
        String jobStartedAt = progress.startedAt() != null ? progress.startedAt() : Timestamps.toIso(Instant.now());

        analytics.capture(user.id(), Event.route_computation_started, Map.of(
                "space_id", space.id(),
                "poi_count", pois.size(),
                "job_started_at", jobStartedAt));

        routeJobService.runInBackground(user.id(), space, pois, jobStartedAt);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Bodies.of("message", "Route computation started", "progress", progress));
    }

    @GetMapping("/{id}/route-progress")
    public ResponseEntity<Map<String, Object>> getRouteProgress(AuthUser user, @PathVariable("id") String rawId) {
        int spaceId = SpaceValidations.spaceId(rawId);
        spaceService.getSpaceForUser(spaceId, user.id());
        return ResponseEntity.ok(Bodies.of("progress", progressService.getRouteProgress(spaceId)));
    }

    @GetMapping("/{id}/routes")
    public ResponseEntity<Map<String, Object>> listRoutes(AuthUser user, @PathVariable("id") String rawId) {
        int spaceId = SpaceValidations.spaceId(rawId);
        Space space = spaceService.getSpaceForUser(spaceId, user.id());
        return ResponseEntity.ok(Bodies.of("routes", routingService.listRoutesForSpace(space.id())));
    }
}
