package com.scout.springserver.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.scout.springserver.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Makes Socket.IO reachable on the main port (PORT, 3000) at /socket.io/,
 * exactly where the Express server served it, by forwarding Engine.IO
 * HTTP long-polling requests to the loopback netty-socketio server.
 *
 * WebSocket upgrade requests are refused, so socket.io-client keeps using
 * HTTP long-polling (its built-in fallback) — events still arrive live,
 * and no frontend change is needed.
 *
 * Runs before every other filter so CORS/security headers come only from
 * netty-socketio's own response, as they did from socket.io.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE - 10)
@ConditionalOnProperty(prefix = "scout.socket-io", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SocketIoProxyFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SocketIoProxyFilter.class);

    private static final String PATH_PREFIX = "/socket.io/";

    /** Headers java.net.http won't let us set, plus hop-by-hop headers. */
    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
        "host", "connection", "content-length", "expect", "upgrade", "keep-alive",
        "transfer-encoding", "te", "trailer", "proxy-authorization", "proxy-connection");

    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
        "connection", "content-length", "transfer-encoding", "keep-alive", "upgrade", ":status");

    private final String upstream;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public SocketIoProxyFilter(AppProperties props) {
        this.upstream = "http://127.0.0.1:" + props.socketIo().port();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/socket.io") || path.startsWith(PATH_PREFIX));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Keep the client on long-polling; the failed upgrade probe is expected and harmless.
        if ("websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "WebSocket transport not available; use polling");
            return;
        }

        String query = request.getQueryString();
        URI target = URI.create(upstream + request.getRequestURI() + (query != null ? "?" + query : ""));

        HttpRequest.Builder forward = HttpRequest.newBuilder(target)
            // Long-poll GETs are held open up to pingInterval + pingTimeout (~45s).
            .timeout(Duration.ofSeconds(90));

        for (String name : Collections.list(request.getHeaderNames())) {
            if (SKIP_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) continue;
            for (String value : Collections.list(request.getHeaders(name))) {
                forward.header(name, value);
            }
        }

        byte[] body = request.getInputStream().readAllBytes();
        forward.method(request.getMethod(), body.length == 0
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(body));

        HttpResponse<InputStream> upstreamResponse;
        try {
            upstreamResponse = httpClient.send(forward.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
            return;
        } catch (IOException e) {
            log.warn("Socket.IO upstream unavailable: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
            return;
        }

        response.setStatus(upstreamResponse.statusCode());
        upstreamResponse.headers().map().forEach((name, values) -> {
            if (SKIP_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) return;
            List<String> copy = values;
            for (String value : copy) response.addHeader(name, value);
        });

        try (InputStream in = upstreamResponse.body(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
