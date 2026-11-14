package com.scout.springserver.controller;

import com.scout.springserver.dto.Requests;
import com.scout.springserver.dto.UniversityCommuteView;
import com.scout.springserver.model.University;
import com.scout.springserver.service.UniversityService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;
import com.scout.springserver.validation.UniversityValidations;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** backend/src/routes/university.routes.ts + backend/src/controllers/university.controller.ts */
@RestController
@RequestMapping("/api/university")
public class UniversityController {

    private final UniversityService universityService;

    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> getUniversityDashboard(AuthUser user) {
        University university = universityService.getUniversityForUser(user.id()).orElse(null);
        List<UniversityCommuteView> commutes = universityService.listUniversityCommutesForUser(user.id());
        return ResponseEntity.ok(Bodies.of("university", university, "commutes", commutes));
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> saveUniversity(
            AuthUser user, @RequestBody(required = false) Map<String, Object> body) {
        Requests.UpsertUniversity input = UniversityValidations.upsertUniversity(body);
        University university = universityService.saveUniversityForUser(user.id(), input.name(), input.address());
        return ResponseEntity.ok(Bodies.of("message", "University saved", "university", university));
    }

    @PostMapping("/commutes")
    public ResponseEntity<Map<String, Object>> computeUniversityCommutes(AuthUser user) {
        List<UniversityCommuteView> commutes = universityService.computeUniversityCommutesForUser(user.id());
        return ResponseEntity.ok(Bodies.of("message", "University commutes computed", "commutes", commutes));
    }
}
