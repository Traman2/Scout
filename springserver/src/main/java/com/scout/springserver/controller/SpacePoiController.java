package com.scout.springserver.controller;

import com.scout.springserver.dto.Requests;
import com.scout.springserver.exception.ApiException;
import com.scout.springserver.model.Poi;
import com.scout.springserver.model.Space;
import com.scout.springserver.service.AnalyticsService;
import com.scout.springserver.service.AnalyticsService.Event;
import com.scout.springserver.service.PoiService;
import com.scout.springserver.service.SpaceService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;
import com.scout.springserver.util.Errors;
import com.scout.springserver.validation.SpaceValidations;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nearby-POI routes on a space — backend/src/controllers/space-poi.controller.ts */
@RestController
@RequestMapping("/api/spaces")
public class SpacePoiController {

    private static final Logger log = LoggerFactory.getLogger(SpacePoiController.class);

    private final SpaceService spaceService;
    private final PoiService poiService;
    private final AnalyticsService analytics;

    public SpacePoiController(SpaceService spaceService, PoiService poiService, AnalyticsService analytics) {
        this.spaceService = spaceService;
        this.poiService = poiService;
        this.analytics = analytics;
    }

    @PostMapping("/{id}/discover")
    public ResponseEntity<Map<String, Object>> discoverPois(
            AuthUser user, @PathVariable("id") String rawId, @RequestBody(required = false) Map<String, Object> body) {
        int spaceId = SpaceValidations.spaceId(rawId);
        Requests.DiscoverPois input = SpaceValidations.discoverPois(body);

        Space space = spaceService.getSpaceForUser(spaceId, user.id()); // 404 if not the caller's

        if (!space.hasCoordinates()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Missing coordinates", "Geocode this space before discovering nearby POIs");
        }

        int radiusMeters = input.radiusMeters() != null ? input.radiusMeters() : PoiService.DEFAULT_POI_RADIUS_METERS;
        long startedAt = System.nanoTime();

        List<Poi> pois;
        try {
            pois = poiService.discoverPoisForSpace(space.id(), space.lat(), space.lng(), radiusMeters);
        } catch (RuntimeException error) {
            analytics.capture(user.id(), Event.poi_discovery_failed, Map.of(
                    "space_id", space.id(),
                    "error_code", AnalyticsService.errorCode(error),
                    "duration_ms", elapsedMillis(startedAt)));

            log.error("Discover POIs error (spaceId={}): {}", spaceId, Errors.message(error));

            if (Errors.isTimeout(error)) {
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Bodies.of(
                        "error", "POI discovery timed out",
                        "message", "The nearby places service timed out. Please try again shortly.",
                        "retryable", true));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Bodies.of("error", "Internal server error"));
        }

        analytics.capture(user.id(), Event.poi_discovery_completed, Map.of(
                "space_id", space.id(),
                "poi_count", pois.size(),
                "radius_meters", radiusMeters,
                "duration_ms", elapsedMillis(startedAt)));

        return ResponseEntity.ok(Bodies.of("message", "POIs discovered", "pois", pois));
    }

    @GetMapping("/{id}/pois")
    public ResponseEntity<Map<String, Object>> listPois(AuthUser user, @PathVariable("id") String rawId) {
        int spaceId = SpaceValidations.spaceId(rawId);
        Space space = spaceService.getSpaceForUser(spaceId, user.id());
        return ResponseEntity.ok(Bodies.of("pois", poiService.listPoisForSpace(space.id())));
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0, Math.round((System.nanoTime() - startedAtNanos) / 1_000_000.0));
    }
}
