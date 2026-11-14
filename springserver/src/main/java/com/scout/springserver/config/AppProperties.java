package com.scout.springserver.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every environment value the server reads, bound from application.yml
 * (which in turn pulls from .env). Required values fail fast at startup,
 * same as the `throw new Error('... is required')` checks in the Express app.
 */
@ConfigurationProperties(prefix = "scout")
public record AppProperties(
        String env,
        String databaseUrl,
        String jwtSecret,
        String cookieSecret,
        String corsOrigin,
        String osmUserAgent,
        String otpUrl,
        SocketIo socketIo,
        Posthog posthog) {

    public AppProperties {
        require(databaseUrl, "DATABASE_URL");
        require(jwtSecret, "JWT_SECRET");
        require(cookieSecret, "COOKIE_SECRET");
        require(corsOrigin, "CORS_ORIGIN");
        if (env == null || env.isBlank()) env = "development";
        if (socketIo == null) socketIo = new SocketIo(true, 3001);
        if (posthog == null) posthog = new Posthog(false, null, null, null);
    }

    public record SocketIo(boolean enabled, int port) {}

    public record Posthog(boolean enabled, String projectToken, String host, String identitySecret) {}

    public boolean isProduction() {
        return "production".equals(env);
    }

    public List<String> allowedOrigins() {
        return Arrays.stream(corsOrigin.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
    }
}
