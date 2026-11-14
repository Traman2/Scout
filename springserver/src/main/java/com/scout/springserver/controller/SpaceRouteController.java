package com.scout.springserver.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scout.springserver.dto.RouteProgressResponseDTO;
import com.scout.springserver.dto.SpaceRouteResponseDTO;
import com.scout.springserver.service.RouteJobService;
import com.scout.springserver.service.RouteProgressService;
import com.scout.springserver.service.RoutingService;
import com.scout.springserver.service.SpaceService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

/** Transit-route routes on a space — backend/src/controllers/space-route.controller.ts */
@RestController
@RequestMapping("/api/spaces")
@Tag(name = "Space Routes", description = "Walk + transit trips from each POI to a space (OpenTripPlanner)")
public class SpaceRouteController {
    private final SpaceService spaceService;
    private final RoutingService routingService;
    private final RouteProgressService routeProgressService;
    private final RouteJobService routeJobService;

    public SpaceRouteController(
            SpaceService spaceService,
            RoutingService routingService,
            RouteProgressService routeProgressService,
            RouteJobService routeJobService) {
        this.spaceService = spaceService;
        this.routingService = routingService;
        this.routeProgressService = routeProgressService;
        this.routeJobService = routeJobService;
    }

    @PostMapping("/{id}/route")
    @Operation(summary = "Start computing routes in the background (202), or report the job already running (200)")
    public ResponseEntity<Map<String, Object>> computeRoutes(AuthUser user, @PathVariable("id") @Positive Integer id) {
        RouteJobService.StartResult result = routeJobService.startRouteComputation(id, user.id());

        if (result.alreadyRunning()) {
            return ResponseEntity.ok().body(Bodies.of(
                "message", "Route computation already in progress",
                "progress", result.progress()));
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Bodies.of("message", "Route computation started", "progress", result.progress()));
    }

    @GetMapping("/{id}/route-progress")
    @Operation(summary = "Get the route job's progress (also pushed live over Socket.IO)")
    public ResponseEntity<Map<String, Object>> getRouteProgress(AuthUser user, @PathVariable("id") @Positive Integer id) {
        spaceService.getOwnedSpace(id, user.id());
        RouteProgressResponseDTO progress = routeProgressService.getRouteProgress(id);
        return ResponseEntity.ok().body(Bodies.of("progress", progress));
    }

    @GetMapping("/{id}/routes")
    @Operation(summary = "List a space's computed routes")
    public ResponseEntity<Map<String, Object>> getRoutes(AuthUser user, @PathVariable("id") @Positive Integer id) {
        List<SpaceRouteResponseDTO> routes = routingService.getRoutesForSpace(id, user.id());
        return ResponseEntity.ok().body(Bodies.of("routes", routes));
    }
}
