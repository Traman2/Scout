package com.scout.springserver.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scout.springserver.dto.DiscoverPoisRequestDTO;
import com.scout.springserver.dto.PoiResponseDTO;
import com.scout.springserver.model.Space;
import com.scout.springserver.service.PoiService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;
import com.scout.springserver.util.Errors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

/** Nearby-POI routes on a space — backend/src/controllers/space-poi.controller.ts */
@RestController
@RequestMapping("/api/spaces")
@Tag(name = "Space POIs", description = "Nearby places (groceries, gyms, transit) discovered around a space")
public class SpacePoiController {
    private static final Logger log = LoggerFactory.getLogger(SpacePoiController.class);

    private final PoiService poiService;

    public SpacePoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    @PostMapping("/{id}/discover")
    @Operation(summary = "Discover POIs around a geocoded space (replaces its previous POIs)")
    public ResponseEntity<Map<String, Object>> discoverPois(
            AuthUser user,
            @PathVariable("id") @Positive Integer id,
            @Validated @RequestBody(required = false) DiscoverPoisRequestDTO discoverPoisRequestDTO) {
        // 404 / 422 thrown here go straight to GlobalExceptionHandler.
        Space space = poiService.getSpaceReadyForDiscovery(id, user.id());

        Integer requestedRadius = discoverPoisRequestDTO != null ? discoverPoisRequestDTO.getRadiusMeters() : null;
        int radiusMeters = requestedRadius != null ? requestedRadius : PoiService.DEFAULT_POI_RADIUS_METERS;

        List<PoiResponseDTO> pois;
        try {
            pois = poiService.discoverPoisForSpace(space, radiusMeters);
        } catch (RuntimeException error) {
            log.error("Discover POIs error (spaceId={}): {}", id, Errors.message(error));

            if (Errors.isTimeout(error)) {
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Bodies.of(
                    "error", "POI discovery timed out",
                    "message", "The nearby places service timed out. Please try again shortly.",
                    "retryable", true));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Bodies.of("error", "Internal server error"));
        }

        return ResponseEntity.ok().body(Bodies.of("message", "POIs discovered", "pois", pois));
    }

    @GetMapping("/{id}/pois")
    @Operation(summary = "List a space's discovered POIs")
    public ResponseEntity<Map<String, Object>> getPois(AuthUser user, @PathVariable("id") @Positive Integer id) {
        List<PoiResponseDTO> pois = poiService.getPoisForSpace(id, user.id());
        return ResponseEntity.ok().body(Bodies.of("pois", pois));
    }
}
