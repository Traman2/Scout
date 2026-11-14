package com.scout.springserver.controller;

import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.Requests;
import com.scout.springserver.exception.DomainExceptions.SpaceNotFoundException;
import com.scout.springserver.model.Space;
import com.scout.springserver.service.AnalyticsService;
import com.scout.springserver.service.AnalyticsService.Event;
import com.scout.springserver.service.GeocodeAttempt;
import com.scout.springserver.service.SpaceService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;
import com.scout.springserver.validation.SpaceValidations;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Space CRUD + geocoding from backend/src/routes/space.routes.ts and
 * backend/src/controllers/space.controller.ts. Every route requires auth
 * (RequireAuthInterceptor), and every query is scoped to the caller's spaces.
 */
@RestController
@RequestMapping("/api/spaces")
public class SpaceController {

    private static final Logger log = LoggerFactory.getLogger(SpaceController.class);

    private final SpaceService spaceService;
    private final AnalyticsService analytics;
    private final ExecutorService executor;

    public SpaceController(
            SpaceService spaceService,
            AnalyticsService analytics,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.spaceService = spaceService;
        this.analytics = analytics;
        this.executor = executor;
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> createSpace(
            AuthUser user, @RequestBody(required = false) Map<String, Object> body) {
        Requests.CreateSpace input = SpaceValidations.createSpace(body);

        SpaceService.SpaceResult result = spaceService.createSpace(user.id(), input.name(), input.address());

        // Analytics after the response, like the Express `void captureCreatedSpace(...)`.
        executor.execute(() -> captureCreatedSpace(user.id(), result.space(), result.geocodeAttempt()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Bodies.of("message", "Space created", "space", result.space()));
    }

    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> listSpaces(AuthUser user) {
        return ResponseEntity.ok(Bodies.of("spaces", spaceService.listSpacesForUser(user.id())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSpace(AuthUser user, @PathVariable("id") String rawId) {
        int spaceId = SpaceValidations.spaceId(rawId);
        return ResponseEntity.ok(Bodies.of("space", spaceService.getSpaceForUser(spaceId, user.id())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateSpace(
            AuthUser user, @PathVariable("id") String rawId, @RequestBody(required = false) Map<String, Object> body) {
        int spaceId = SpaceValidations.spaceId(rawId);
        Requests.UpdateSpace input = SpaceValidations.updateSpace(body);

        SpaceService.SpaceResult result = spaceService.updateSpaceForUser(spaceId, user.id(), input);
        captureAutomaticGeocodeAttempt(user.id(), result.space().id(), result.geocodeAttempt());

        return ResponseEntity.ok(Bodies.of("message", "Space updated", "space", result.space()));
    }

    @PostMapping("/{id}/geocode")
    public ResponseEntity<Map<String, Object>> geocodeSpace(AuthUser user, @PathVariable("id") String rawId) {
        int spaceId = SpaceValidations.spaceId(rawId);

        Space space;
        try {
            space = spaceService.geocodeSpace(spaceId, user.id());
        } catch (RuntimeException error) {
            if (!(error instanceof SpaceNotFoundException)) {
                analytics.capture(user.id(), Event.space_geocode_failed, Map.of(
                        "space_id", spaceId,
                        "error_code", AnalyticsService.errorCode(error)));
            }
            throw error; // AddressNotFound → 422, SpaceNotFound → 404 (GlobalExceptionHandler)
        }

        analytics.capture(user.id(), Event.space_geocoded, Map.of("space_id", spaceId));
        return ResponseEntity.ok(Bodies.of("message", "Space geocoded", "space", space));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSpace(AuthUser user, @PathVariable("id") String rawId) {
        int spaceId = SpaceValidations.spaceId(rawId);
        spaceService.deleteSpaceForUser(spaceId, user.id());
        return ResponseEntity.ok(Bodies.of("message", "Space deleted"));
    }

    private void captureAutomaticGeocodeAttempt(int ownerId, int spaceId, GeocodeAttempt attempt) {
        if (attempt == null) return;

        if (attempt instanceof GeocodeAttempt.Succeeded) {
            analytics.capture(ownerId, Event.space_geocoded, Map.of("space_id", spaceId));
            return;
        }

        Throwable error = ((GeocodeAttempt.Failed) attempt).error();
        analytics.capture(ownerId, Event.space_geocode_failed, Map.of(
                "space_id", spaceId,
                "error_code", AnalyticsService.errorCode(error)));
    }

    private void captureCreatedSpace(int ownerId, Space space, GeocodeAttempt attempt) {
        Integer spaceOrdinal = null;
        try {
            spaceOrdinal = spaceService.listSpacesForUser(ownerId).size();
        } catch (RuntimeException e) {
            log.warn("Unable to calculate space ordinal for analytics");
        }

        // HashMap, not Map.of — these values may be null.
        Map<String, Object> props = new HashMap<>();
        props.put("space_id", space.id());
        props.put("space_ordinal", spaceOrdinal);
        props.put("is_first_space", spaceOrdinal == null ? null : spaceOrdinal == 1);
        analytics.capture(ownerId, Event.space_created, props);

        captureAutomaticGeocodeAttempt(ownerId, space.id(), attempt);
    }
}
