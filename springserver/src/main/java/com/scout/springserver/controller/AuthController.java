package com.scout.springserver.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scout.springserver.dto.SignInRequestDTO;
import com.scout.springserver.dto.SignUpRequestDTO;
import com.scout.springserver.dto.UserResponseDTO;
import com.scout.springserver.service.AnalyticsService;
import com.scout.springserver.service.AnalyticsService.Event;
import com.scout.springserver.service.AuthService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.Bodies;
import com.scout.springserver.util.JwtUtil;
import com.scout.springserver.util.SignedCookies;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

/** backend/src/routes/auth.routes.ts + backend/src/controllers/auth.controller.ts */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Sign up, sign in, sign out and the current session (signed `token` cookie)")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final JwtUtil jwtUtil;
    private final SignedCookies signedCookies;

    public AuthController(
            AuthService authService,
            AnalyticsService analyticsService,
            JwtUtil jwtUtil,
            SignedCookies signedCookies) {
        this.authService = authService;
        this.analyticsService = analyticsService;
        this.jwtUtil = jwtUtil;
        this.signedCookies = signedCookies;
    }

    @PostMapping("/sign-up")
    @Operation(summary = "Create an account and start a session")
    public ResponseEntity<Map<String, Object>> signup(
            @Validated @RequestBody SignUpRequestDTO signUpRequestDTO, HttpServletResponse response) {
        UserResponseDTO user = authService.createUser(signUpRequestDTO);

        startSession(response, user);
        analyticsService.capture(user.getId(), Event.user_signed_up, Map.of("role", user.getRole()));
        log.info("User registered successfully {}", user.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Bodies.of("message", "User registered", "user", user));
    }

    @PostMapping("/sign-in")
    @Operation(summary = "Sign in and start a session")
    public ResponseEntity<Map<String, Object>> signin(
            @Validated @RequestBody SignInRequestDTO signInRequestDTO, HttpServletResponse response) {
        UserResponseDTO user = authService.authenticateUser(signInRequestDTO);

        startSession(response, user);
        analyticsService.capture(user.getId(), Event.user_signed_in, Map.of("role", user.getRole()));
        log.info("User signed in successfully {}", user.getEmail());

        return ResponseEntity.ok().body(Bodies.of("message", "User signed in", "user", user));
    }

    @PostMapping("/sign-out")
    @Operation(summary = "End the session (clears the token cookie)")
    public ResponseEntity<Map<String, Object>> signout(HttpServletResponse response) {
        signedCookies.clear(response, "token");
        log.info("User signed out successfully");
        return ResponseEntity.ok().body(Bodies.of("message", "User signed out"));
    }

    /** Requires auth (RequireAuthInterceptor, see WebConfig). */
    @GetMapping("/me")
    @Operation(summary = "Get the signed-in user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(AuthUser authUser) {
        UserResponseDTO user = authService.getUserById(authUser.id());
        return ResponseEntity.ok().body(Bodies.of("user", user));
    }

    private void startSession(HttpServletResponse response, UserResponseDTO user) {
        String token = jwtUtil.sign(user.getId(), user.getEmail(), user.getRole());
        signedCookies.set(response, "token", token);
    }
}
