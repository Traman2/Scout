package com.scout.springserver.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.CreateSpaceRequestDTO;
import com.scout.springserver.dto.SpaceResponseDTO;
import com.scout.springserver.dto.UpdateSpaceRequestDTO;
import com.scout.springserver.exception.SpaceNotFoundException;
import com.scout.springserver.service.AnalyticsService;
import com.scout.springserver.service.AnalyticsService.Event;
import com.scout.springserver.service.GeocodeAttempt;
import com.scout.springserver.service.SpaceService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

/**
 * Space CRUD + geocoding — backend/src/routes/space.routes.ts and
 * backend/src/controllers/space.controller.ts. Every route requires auth
 * (RequireAuthInterceptor), and every lookup is scoped to the caller's spaces.
 */
@RestController
@RequestMapping("/api/spaces")
@Tag(name = "Space", description = "The signed-in user's saved apartments/spaces")
public class SpaceController {
    private static final Logger log = LoggerFactory.getLogger(SpaceController.class);

    private final SpaceService spaceService;
    private final AnalyticsService analyticsService;
    private final ExecutorService executor;

    public SpaceController(
            SpaceService spaceService,
            AnalyticsService analyticsService,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.spaceService = spaceService;
        this.analyticsService = analyticsService;
        this.executor = executor;
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Create a space (geocodes the address automatically)")
    public ResponseEntity<Map<String, Object>> createSpace(
            AuthUser user, @Validated @RequestBody CreateSpaceRequestDTO createSpaceRequestDTO) {
        SpaceService.SpaceResult result = spaceService.createSpace(user.id(), createSpaceRequestDTO);

        // Analytics after the response, like the Express `void captureCreatedSpace(...)`.
        executor.execute(() -> captureCreatedSpace(user.id(), result.space().getId(), result.geocodeAttempt()));

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Bodies.of("message", "Space created", "space", result.space()));
    }

    @GetMapping({"", "/"})
    @Operation(summary = "List the signed-in user's spaces")
    public ResponseEntity<Map<String, Object>> getSpaces(AuthUser user) {
        List<SpaceResponseDTO> spaces = spaceService.getSpaces(user.id());
        return ResponseEntity.ok().body(Bodies.of("spaces", spaces));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a space")
    public ResponseEntity<Map<String, Object>> getSpace(AuthUser user, @PathVariable("id") @Positive Integer id) {
        SpaceResponseDTO space = spaceService.getSpace(id, user.id());
        return ResponseEntity.ok().body(Bodies.of("space", space));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a space (re-geocodes when the address changes)")
    public ResponseEntity<Map<String, Object>> updateSpace(
            AuthUser user,
            @PathVariable("id") @Positive Integer id,
            @Validated @RequestBody UpdateSpaceRequestDTO updateSpaceRequestDTO) {
        SpaceService.SpaceResult result = spaceService.updateSpace(id, user.id(), updateSpaceRequestDTO);
        captureAutomaticGeocodeAttempt(user.id(), result.space().getId(), result.geocodeAttempt());

        return ResponseEntity.ok().body(Bodies.of("message", "Space updated", "space", result.space()));
    }

    @PostMapping("/{id}/geocode")
    @Operation(summary = "Retry geocoding a space's address")
    public ResponseEntity<Map<String, Object>> geocodeSpace(AuthUser user, @PathVariable("id") @Positive Integer id) {
        SpaceResponseDTO space;
        try {
            space = spaceService.geocodeSpace(id, user.id());
        } catch (RuntimeException error) {
            if (!(error instanceof SpaceNotFoundException)) {
                analyticsService.capture(user.id(), Event.space_geocode_failed, Map.of(
                    "space_id", id,
                    "error_code", AnalyticsService.errorCode(error)));
            }
            throw error; // AddressNotFound → 422, SpaceNotFound → 404 (GlobalExceptionHandler)
        }

        analyticsService.capture(user.id(), Event.space_geocoded, Map.of("space_id", id));
        return ResponseEntity.ok().body(Bodies.of("message", "Space geocoded", "space", space));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a space (and its POI links, routes and commutes)")
    public ResponseEntity<Map<String, Object>> deleteSpace(AuthUser user, @PathVariable("id") @Positive Integer id) {
        spaceService.deleteSpace(id, user.id());
        return ResponseEntity.ok().body(Bodies.of("message", "Space deleted"));
    }

    private void captureAutomaticGeocodeAttempt(Integer ownerId, Integer spaceId, GeocodeAttempt attempt) {
        if (attempt == null) return;

        if (attempt instanceof GeocodeAttempt.Failed failed) {
            analyticsService.capture(ownerId, Event.space_geocode_failed, Map.of(
                "space_id", spaceId,
                "error_code", AnalyticsService.errorCode(failed.error())));
            return;
        }

        analyticsService.capture(ownerId, Event.space_geocoded, Map.of("space_id", spaceId));
    }

    private void captureCreatedSpace(Integer ownerId, Integer spaceId, GeocodeAttempt attempt) {
        Long spaceOrdinal = null;
        try {
            spaceOrdinal = spaceService.countSpaces(ownerId);
        } catch (RuntimeException e) {
            log.warn("Unable to calculate space ordinal for analytics");
        }

        // HashMap, not Map.of — these values may be null.
        Map<String, Object> properties = new HashMap<>();
        properties.put("space_id", spaceId);
        properties.put("space_ordinal", spaceOrdinal);
        properties.put("is_first_space", spaceOrdinal == null ? null : spaceOrdinal == 1);
        analyticsService.capture(ownerId, Event.space_created, properties);

        captureAutomaticGeocodeAttempt(ownerId, spaceId, attempt);
    }
}
