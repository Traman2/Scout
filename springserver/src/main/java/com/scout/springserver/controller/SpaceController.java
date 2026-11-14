package com.scout.springserver.controller;

import java.util.List;
import java.util.Map;

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

import com.scout.springserver.dto.CreateSpaceRequestDTO;
import com.scout.springserver.dto.SpaceResponseDTO;
import com.scout.springserver.dto.UpdateSpaceRequestDTO;
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
    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Create a space (geocodes the address automatically)")
    public ResponseEntity<Map<String, Object>> createSpace(
            AuthUser user, @Validated @RequestBody CreateSpaceRequestDTO createSpaceRequestDTO) {
        SpaceResponseDTO space = spaceService.createSpace(user.id(), createSpaceRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Bodies.of("message", "Space created", "space", space));
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
        SpaceResponseDTO space = spaceService.updateSpace(id, user.id(), updateSpaceRequestDTO);
        return ResponseEntity.ok().body(Bodies.of("message", "Space updated", "space", space));
    }

    @PostMapping("/{id}/geocode")
    @Operation(summary = "Retry geocoding a space's address")
    public ResponseEntity<Map<String, Object>> geocodeSpace(AuthUser user, @PathVariable("id") @Positive Integer id) {
        // AddressNotFound → 422, SpaceNotFound → 404 (GlobalExceptionHandler)
        SpaceResponseDTO space = spaceService.geocodeSpace(id, user.id());
        return ResponseEntity.ok().body(Bodies.of("message", "Space geocoded", "space", space));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a space (and its POI links, routes and commutes)")
    public ResponseEntity<Map<String, Object>> deleteSpace(AuthUser user, @PathVariable("id") @Positive Integer id) {
        spaceService.deleteSpace(id, user.id());
        return ResponseEntity.ok().body(Bodies.of("message", "Space deleted"));
    }
}
