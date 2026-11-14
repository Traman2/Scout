package com.scout.springserver.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects to the same Neon Postgres database as the Express server
 * (backend/src/configs/database.ts), using the same DATABASE_URL.
 *
 * Neon hands out a libpq-style URL (postgresql://user:pass@host/db?sslmode=require&channel_binding=require),
 * which JDBC can't take directly, so it's split into a jdbc:postgresql URL plus
 * separate username/password here.
 *
 * Unlike the neon-http driver, this is a real pooled TCP connection, so
 * services can (and do) use transactions.
 */
@Configuration
public class DatabaseConfig {

    @Bean(destroyMethod = "close")
    public DataSource dataSource(AppProperties props) {
        URI uri = URI.create(props.databaseUrl().trim());

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            username = decode(colon >= 0 ? userInfo.substring(0, colon) : userInfo);
            password = colon >= 0 ? decode(userInfo.substring(colon + 1)) : null;
        }

        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost()).append(':').append(port).append('/').append(database);

        List<String> params = new ArrayList<>();
        if (uri.getRawQuery() != null) {
            for (String pair : uri.getRawQuery().split("&")) {
                if (pair.isBlank()) continue;
                // libpq spells it channel_binding; pgjdbc spells it channelBinding.
                params.add(pair.startsWith("channel_binding=")
                        ? pair.replaceFirst("channel_binding=", "channelBinding=")
                        : pair);
            }
        }
        if (params.stream().noneMatch(p -> p.startsWith("sslmode="))) {
            params.add("sslmode=require");
        }
        jdbcUrl.append('?').append(String.join("&", params));

        HikariConfig config = new HikariConfig();
        config.setPoolName("neon");
        config.setJdbcUrl(jdbcUrl.toString());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(0);
        // Neon suspends idle computes and drops their connections, so don't
        // hold connections open for long.
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(300_000);
        config.setKeepaliveTime(60_000);
        config.setConnectionTimeout(30_000);
        return new HikariDataSource(config);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
