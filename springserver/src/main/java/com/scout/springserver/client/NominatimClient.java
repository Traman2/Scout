package com.scout.springserver.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.scout.springserver.config.AppProperties;
import com.scout.springserver.util.Throttle;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Address → coordinates via OpenStreetMap Nominatim (backend/src/clients/nominatim.client.ts).
 *
 * Nominatim's usage policy requires an identifying User-Agent (OSM_USER_AGENT)
 * and at most 1 request/second. This client is the only caller, so its own
 * throttle serializes requests app-wide.
 */
@Component
public class NominatimClient {

    private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";

    private final RestClient http = HttpClients.create(Duration.ofSeconds(20));
    private final Throttle throttle = new Throttle(1000);
    private final String userAgent;

    public NominatimClient(AppProperties props) {
        this.userAgent = HttpClients.userAgent(props.osmUserAgent());
        if (props.osmUserAgent() == null || props.osmUserAgent().isBlank()) {
            log.warn("OSM_USER_AGENT is not set — using a generic fallback. "
                    + "Nominatim may rate-limit or block requests without real contact info.");
        }
    }

    /** Empty when Nominatim has no match; throws when the request itself fails. */
    public Optional<Coordinates> geocodeAddress(String address) {
        throttle.acquire();

        URI uri = UriComponentsBuilder.fromUriString(NOMINATIM_URL)
                .queryParam("q", "{q}")
                .queryParam("format", "json")
                .queryParam("limit", 1)
                .encode()
                .buildAndExpand(address)
                .toUri();

        try {
            JsonNode results = http.get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .retrieve()
                    .body(JsonNode.class);

            if (results == null || !results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }

            JsonNode first = results.get(0);
            return Optional.of(new Coordinates(
                    Double.parseDouble(first.path("lat").asText()),
                    Double.parseDouble(first.path("lon").asText())));
        } catch (RestClientException | NumberFormatException e) {
            log.error("Error geocoding address via Nominatim: {}", e.getMessage());
            throw e;
        }
    }
}
