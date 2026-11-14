package com.scout.springserver.service;

import com.scout.springserver.config.AppProperties;
import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.util.Errors;
import com.scout.springserver.util.Timestamps;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * PostHog product analytics (backend/src/configs/analytics.ts).
 *
 * - Users are identified by a pseudonymous HMAC of their id ("scout_" + hex),
 *   never by id or email. analyticsId is returned to the frontend even when
 *   PostHog is disabled, same as the Express server.
 * - Only an allow-listed set of properties per event is ever sent.
 * - Delivery is fire-and-forget on a background thread; failures are logged
 *   without payload data and never affect the request.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    public enum Event {
        user_signed_up("role"),
        user_signed_in("role"),
        space_created("space_id", "space_ordinal", "is_first_space"),
        space_geocoded("space_id"),
        space_geocode_failed("space_id", "error_code"),
        poi_discovery_completed("space_id", "poi_count", "radius_meters", "duration_ms"),
        poi_discovery_failed("space_id", "error_code", "duration_ms"),
        route_computation_started("space_id", "poi_count", "job_started_at"),
        route_computation_completed("space_id", "poi_count", "route_count", "duration_ms"),
        route_computation_failed("space_id", "poi_count", "completed_count", "duration_ms", "error_code");

        private final List<String> propertyKeys;

        Event(String... propertyKeys) {
            this.propertyKeys = List.of(propertyKeys);
        }
    }

    private static final Pattern GATEWAY_STATUS = Pattern.compile("\\b50[234]\\b");

    private final boolean enabled;
    private final String projectToken;
    private final String host;
    private final String identitySecret;
    private final String environment;
    private final ExecutorService executor;
    private final RestClient http;

    public AnalyticsService(AppProperties props, @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        AppProperties.Posthog posthog = props.posthog();
        this.enabled = posthog.enabled();
        this.projectToken = trimToNull(posthog.projectToken());
        this.host = trimToNull(posthog.host());
        String dedicatedSecret = trimToNull(posthog.identitySecret());
        this.identitySecret = dedicatedSecret != null ? dedicatedSecret : trimToNull(props.jwtSecret());
        this.environment = props.env();
        this.executor = executor;

        if (enabled && (projectToken == null || host == null || identitySecret == null)) {
            throw new IllegalStateException("POSTHOG_PROJECT_TOKEN, POSTHOG_HOST, and POSTHOG_IDENTITY_SECRET "
                    + "or JWT_SECRET are required when PostHog is enabled");
        }

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Stable pseudonymous id: "scout_" + HMAC-SHA256(secret, "scout:posthog:user:{id}") in hex. */
    public String getDistinctId(int userId) {
        if (identitySecret == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(identitySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(("scout:posthog:user:" + userId).getBytes(StandardCharsets.UTF_8));
            return "scout_" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not compute analytics id", e);
        }
    }

    /** Keeps only the allow-listed keys for the event, with primitive (or null) values. */
    public static Map<String, Object> filterProperties(Event event, Map<String, ?> properties) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String key : event.propertyKeys) {
            if (!properties.containsKey(key)) continue;
            Object value = properties.get(key);
            if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
                filtered.put(key, value);
            }
        }
        return filtered;
    }

    public void capture(int userId, Event event, Map<String, ?> properties) {
        if (!enabled) return;

        String distinctId = getDistinctId(userId);
        if (distinctId == null) return;

        Map<String, Object> props = filterProperties(event, properties);
        props.put("environment", environment);

        Map<String, Object> payload = new HashMap<>();
        payload.put("api_key", projectToken);
        payload.put("event", event.name());
        payload.put("distinct_id", distinctId);
        payload.put("properties", props);
        payload.put("timestamp", Timestamps.toIso(Instant.now()));

        try {
            executor.execute(() -> {
                try {
                    http.post()
                            .uri(host.replaceAll("/+$", "") + "/capture/")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(payload)
                            .retrieve()
                            .toBodilessEntity();
                } catch (RuntimeException e) {
                    log.warn("PostHog event delivery failed");
                }
            });
        } catch (RuntimeException e) {
            log.warn("PostHog event capture failed");
        }
    }

    /** Buckets an error into a coarse, non-identifying code (getAnalyticsErrorCode). */
    public static String errorCode(Throwable error) {
        if (error == null) return "unknown";
        if (Errors.isTimeout(error)) return "timeout";

        String message = Errors.message(error).toLowerCase(Locale.ROOT);
        if (message.equals("address not found")) return "address_not_found";
        if (message.contains("timeout") || message.contains("timed out") || message.contains("aborted")) {
            return "timeout";
        }
        if (message.contains("fetch failed")
                || message.contains("network")
                || message.contains("econn")
                || message.contains("connect")
                || message.contains("rate limit")
                || message.contains("service unavailable")
                || GATEWAY_STATUS.matcher(message).find()) {
            return "provider_unavailable";
        }
        return "unknown";
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
