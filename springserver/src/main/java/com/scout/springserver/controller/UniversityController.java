package com.scout.springserver.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scout.springserver.dto.UniversityCommuteResponseDTO;
import com.scout.springserver.dto.UniversityRequestDTO;
import com.scout.springserver.dto.UniversityResponseDTO;
import com.scout.springserver.service.UniversityService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** backend/src/routes/university.routes.ts + backend/src/controllers/university.controller.ts */
@RestController
@RequestMapping("/api/university")
@Tag(name = "University", description = "The user's university and commute times from each space")
public class UniversityController {
    private final UniversityService universityService;

    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Get the university and its commutes (university is null if not set)")
    public ResponseEntity<Map<String, Object>> getUniversityDashboard(AuthUser user) {
        UniversityResponseDTO university = universityService.getUniversity(user.id());
        List<UniversityCommuteResponseDTO> commutes = universityService.getCommutes(user.id());
        return ResponseEntity.ok().body(Bodies.of("university", university, "commutes", commutes));
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Save (create or replace) the university")
    public ResponseEntity<Map<String, Object>> saveUniversity(
            AuthUser user, @Validated @RequestBody UniversityRequestDTO universityRequestDTO) {
        UniversityResponseDTO university = universityService.saveUniversity(user.id(), universityRequestDTO);
        return ResponseEntity.ok().body(Bodies.of("message", "University saved", "university", university));
    }

    @PostMapping("/commutes")
    @Operation(summary = "Compute commutes from every geocoded space to the university")
    public ResponseEntity<Map<String, Object>> computeUniversityCommutes(AuthUser user) {
        List<UniversityCommuteResponseDTO> commutes = universityService.computeCommutes(user.id());
        return ResponseEntity.ok().body(Bodies.of("message", "University commutes computed", "commutes", commutes));
    }
}
