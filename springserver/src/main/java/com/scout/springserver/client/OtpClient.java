package com.scout.springserver.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.scout.springserver.config.AppProperties;
import com.scout.springserver.model.RouteGeometryLeg;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Walk + transit trip planning via the self-hosted OpenTripPlanner GraphQL API
 * (backend/src/clients/otp.client.ts).
 *
 * OTP_URL is read lazily so the rest of the app still boots when OTP isn't
 * running. No throttle — OTP is self-hosted, not a shared public instance.
 */
@Component
public class OtpClient {

    private static final Logger log = LoggerFactory.getLogger(OtpClient.class);

    private static final String PLAN_QUERY = """
              query Plan(
                $fromLat: Float!
                $fromLon: Float!
                $toLat: Float!
                $toLon: Float!
                $includeGeometry: Boolean!
              ) {
                plan(
                  from: { lat: $fromLat, lon: $fromLon }
                  to: { lat: $toLat, lon: $toLon }
                  transportModes: [{ mode: WALK }, { mode: TRANSIT }]
                  numItineraries: 1
                ) {
                  itineraries {
                    duration
                    legs {
                      mode
                      duration
                      legGeometry @include(if: $includeGeometry) {
                        points
                      }
                    }
                  }
                }
              }
            """;

    /**
     * Trip durations split by mode, plus per-leg geometry (TripPlan).
     * durationSeconds always equals the sum of every bucket including wait.
     */
    public record TripPlan(
            int durationSeconds,
            int walkDurationSeconds,
            int metroDurationSeconds,
            int busDurationSeconds,
            int tramDurationSeconds,
            int otherDurationSeconds,
            int waitDurationSeconds,
            List<RouteGeometryLeg> routeGeometry) {}

    // 2 minutes, not the 20s the other clients use — the local OTP instance is
    // resource-capped and can be slow to respond under memory/CPU pressure.
    private final RestClient http = HttpClients.create(Duration.ofMinutes(2));
    private final AppProperties props;

    public OtpClient(AppProperties props) {
        this.props = props;
    }

    public Optional<TripPlan> planTripWithGeometry(Coordinates from, Coordinates to) {
        return requestTrip(from, to, true);
    }

    public Optional<TripPlan> planTrip(Coordinates from, Coordinates to) {
        return requestTrip(from, to, false);
    }

    private Optional<TripPlan> requestTrip(Coordinates from, Coordinates to, boolean includeGeometry) {
        String otpUrl = getOtpUrl();

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("fromLat", from.lat());
        variables.put("fromLon", from.lng());
        variables.put("toLat", to.lat());
        variables.put("toLon", to.lng());
        variables.put("includeGeometry", includeGeometry);

        try {
            JsonNode res = http.post()
                    .uri(otpUrl + "/otp/gtfs/v1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", PLAN_QUERY, "variables", variables))
                    .retrieve()
                    .body(JsonNode.class);

            if (res == null) return Optional.empty();

            JsonNode errors = res.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                List<String> messages = new ArrayList<>();
                errors.forEach(e -> messages.add(e.path("message").asText()));
                throw new IllegalStateException("OTP GraphQL error: " + String.join("; ", messages));
            }

            JsonNode itineraries = res.path("data").path("plan").path("itineraries");
            if (!itineraries.isArray() || itineraries.isEmpty()) return Optional.empty();

            return Optional.of(toTripPlan(itineraries.get(0)));
        } catch (RuntimeException e) {
            // Rethrow a clean error with a readable message, same as the Express client.
            String message = describe(e);
            log.error("Error planning trip via OTP: {}", message);
            throw new IllegalStateException("OTP request failed: " + message, e);
        }
    }

    private static TripPlan toTripPlan(JsonNode itinerary) {
        int walk = 0, metro = 0, bus = 0, tram = 0, other = 0;
        List<RouteGeometryLeg> geometry = new ArrayList<>();

        // v1: a fixed mapping from OTP's leg.mode to one of Madrid's three
        // loaded GTFS networks (Metro, EMT bus, Metro Ligero tram). Anything
        // else falls into `other` rather than being silently dropped.
        for (JsonNode leg : itinerary.path("legs")) {
            String mode = leg.path("mode").asText();
            int duration = (int) Math.round(leg.path("duration").asDouble());
            switch (mode) {
                case "WALK" -> walk += duration;
                case "SUBWAY" -> metro += duration;
                case "BUS" -> bus += duration;
                case "TRAM" -> tram += duration;
                default -> other += duration;
            }

            String points = leg.path("legGeometry").path("points").asText(null);
            if (points != null && !points.isEmpty()) {
                geometry.add(new RouteGeometryLeg(mode, points));
            }
        }

        // itinerary.duration is door-to-door; legs only cover time moving, not
        // time waiting at a stop between legs. The difference is wait time.
        int total = (int) Math.round(itinerary.path("duration").asDouble());
        int legsTotal = walk + metro + bus + tram + other;
        int wait = Math.max(0, total - legsTotal);

        return new TripPlan(total, walk, metro, bus, tram, other, wait, geometry);
    }

    private String getOtpUrl() {
        String url = props.otpUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "OTP_URL is not set — start the OTP instance (see docker/otp/README.md) and set OTP_URL to reach it");
        }
        return url.replaceAll("/+$", "");
    }

    private static String describe(RuntimeException e) {
        if (e instanceof HttpStatusCodeException http) {
            return http.getStatusText() + " (status " + http.getStatusCode().value() + ")";
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        return root == e ? message : root.getClass().getSimpleName() + ": " + message;
    }
}
