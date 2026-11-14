package com.scout.springserver.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Shared RestClient setup for the external API clients (axios in the Express server). */
final class HttpClients {

    private HttpClients() {}

    static RestClient create(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                // Avoid HTTP/2 upgrade quirks with some public/self-hosted endpoints.
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder().requestFactory(factory).build();
    }

    static String userAgent(String configured) {
        return configured == null || configured.isBlank()
                ? "Scout/1.0 (student housing app; set OSM_USER_AGENT with contact info)"
                : configured;
    }
}
