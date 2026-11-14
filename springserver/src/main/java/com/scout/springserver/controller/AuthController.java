package com.scout.springserver.controller;

import com.scout.springserver.dto.Requests;
import com.scout.springserver.dto.UserResponse;
import com.scout.springserver.model.User;
import com.scout.springserver.service.AnalyticsService;
import com.scout.springserver.service.AnalyticsService.Event;
import com.scout.springserver.service.AuthService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;
import com.scout.springserver.util.JwtUtil;
import com.scout.springserver.util.SignedCookies;
import com.scout.springserver.validation.AuthValidations;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** backend/src/routes/auth.routes.ts + backend/src/controllers/auth.controller.ts */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AnalyticsService analytics;
    private final JwtUtil jwt;
    private final SignedCookies cookies;

    public AuthController(AuthService authService, AnalyticsService analytics, JwtUtil jwt, SignedCookies cookies) {
        this.authService = authService;
        this.analytics = analytics;
        this.jwt = jwt;
        this.cookies = cookies;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<Map<String, Object>> signup(
            @RequestBody(required = false) Map<String, Object> body, HttpServletResponse res) {
        Requests.SignUp input = AuthValidations.signup(body);

        User user = authService.createUser(input.name(), input.email(), input.password());

        cookies.set(res, "token", jwt.sign(user.id(), user.email(), user.role()));
        analytics.capture(user.id(), Event.user_signed_up, Map.of("role", user.role()));
        log.info("User registered successfully {}", input.email());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Bodies.of("message", "User registered", "user", toResponse(user)));
    }

    @PostMapping("/sign-in")
    public ResponseEntity<Map<String, Object>> signin(
            @RequestBody(required = false) Map<String, Object> body, HttpServletResponse res) {
        Requests.SignIn input = AuthValidations.signIn(body);

        User user = authService.authenticateUser(input.email(), input.password());

        cookies.set(res, "token", jwt.sign(user.id(), user.email(), user.role()));
        analytics.capture(user.id(), Event.user_signed_in, Map.of("role", user.role()));
        log.info("User signed in successfully {}", input.email());

        return ResponseEntity.ok(Bodies.of("message", "User signed in", "user", toResponse(user)));
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Map<String, Object>> signout(HttpServletResponse res) {
        cookies.clear(res, "token");
        log.info("User signed out successfully");
        return ResponseEntity.ok(Bodies.of("message", "User signed out"));
    }

    /** Protected by RequireAuthInterceptor (see WebConfig). */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(AuthUser authUser) {
        User user = authService.getUserById(authUser.id());
        return ResponseEntity.ok(Bodies.of("user", toResponse(user)));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.id(), analytics.getDistinctId(user.id()), user.name(), user.email(), user.role());
    }
}
