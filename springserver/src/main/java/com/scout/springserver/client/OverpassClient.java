package com.scout.springserver.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.scout.springserver.config.AppProperties;
import com.scout.springserver.util.Throttle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Nearby POI discovery via the OSM Overpass API (backend/src/clients/overpass.client.ts).
 * Throttled to 1 request/second app-wide, retried up to 3 times across two
 * mirrors with exponential backoff on network errors, 429 and 5xx.
 */
@Component
public class OverpassClient {

    private static final List<String> OVERPASS_URLS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter");
    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(QUERY_TIMEOUT_SECONDS + 5);
    private static final int MAX_ATTEMPTS = 3;

    /** v1: a fixed category → OSM tag mapping. Not user-configurable yet. */
    private record CategoryFilter(String category, String key, String value) {}

    private static final List<CategoryFilter> CATEGORY_FILTERS = List.of(
            new CategoryFilter("grocery", "shop", "supermarket"),
            new CategoryFilter("gym", "leisure", "fitness_centre"),
            new CategoryFilter("transit", "highway", "bus_stop"),
            new CategoryFilter("transit", "railway", "station"));

    /** A POI as returned by Overpass, before it's stored (OverpassPoi). */
    public record OverpassPoi(long osmId, String category, String name, double lat, double lng) {}

    private final RestClient http = HttpClients.create(HTTP_TIMEOUT);
    private final Throttle throttle = new Throttle(1000);
    private final String userAgent;

    public OverpassClient(AppProperties props) {
        this.userAgent = HttpClients.userAgent(props.osmUserAgent());
    }

    public List<OverpassPoi> findNearbyPois(double lat, double lng, int radiusMeters) {
        JsonNode response = queryOverpass(buildQuery(lat, lng, radiusMeters));

        List<OverpassPoi> pois = new ArrayList<>();
        for (JsonNode el : response.path("elements")) {
            JsonNode tags = el.path("tags");
            String name = tags.hasNonNull("name") ? tags.get("name").asText() : null;
            String category = categorize(tags);

            JsonNode position = el.has("center") ? el.get("center") : el;
            boolean hasPosition = position.hasNonNull("lat") && position.hasNonNull("lon");

            if (name != null && !name.isEmpty() && category != null && hasPosition) {
                pois.add(new OverpassPoi(
                        el.path("id").asLong(),
                        category,
                        name,
                        position.get("lat").asDouble(),
                        position.get("lon").asDouble()));
            }
        }
        return pois;
    }

    private JsonNode queryOverpass(String query) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("data", query);

        for (int attempt = 0; ; attempt++) {
            throttle.acquire();
            try {
                JsonNode body = http.post()
                        .uri(OVERPASS_URLS.get(attempt % OVERPASS_URLS.size()))
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                if (body == null) throw new IllegalStateException("Empty response from Overpass");
                return body;
            } catch (RuntimeException e) {
                if (attempt + 1 >= MAX_ATTEMPTS || !isRetryable(e)) throw e;
                sleep(1000L * (1L << attempt));
            }
        }
    }

    private static boolean isRetryable(RuntimeException e) {
        // Timeouts, connection resets, DNS failures, ... all surface as ResourceAccessException.
        if (e instanceof ResourceAccessException) return true;
        if (e instanceof HttpStatusCodeException http) {
            HttpStatusCode status = http.getStatusCode();
            return status.value() == 429 || status.is5xxServerError();
        }
        return false;
    }

    private static String categorize(JsonNode tags) {
        if (tags == null || tags.isMissingNode()) return null;
        for (CategoryFilter f : CATEGORY_FILTERS) {
            if (f.value().equals(tags.path(f.key()).asText(null))) return f.category();
        }
        return null;
    }

    private static String buildQuery(double lat, double lng, int radiusMeters) {
        String clauses = CATEGORY_FILTERS.stream()
                .map(f -> "node[\"" + f.key() + "\"=\"" + f.value() + "\"](around:"
                        + radiusMeters + "," + lat + "," + lng + ");")
                .collect(Collectors.joining("\n  "));
        return "[out:json][timeout:" + QUERY_TIMEOUT_SECONDS + "];\n(\n  " + clauses + "\n);\nout center;";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Overpass", e);
        }
    }
}
