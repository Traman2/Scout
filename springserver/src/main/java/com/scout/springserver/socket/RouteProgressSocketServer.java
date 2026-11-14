package com.scout.springserver.socket;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.scout.springserver.config.AppProperties;
import com.scout.springserver.config.AsyncConfig;
import com.scout.springserver.dto.RouteProgressResponseDTO;
import com.scout.springserver.service.RouteProgressEvent;
import com.scout.springserver.service.RouteProgressService;
import com.scout.springserver.service.SpaceService;
import com.scout.springserver.util.AuthUser;
import com.scout.springserver.util.JwtUtil;
import com.scout.springserver.util.SignedCookies;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Live route progress over Socket.IO (the io server in backend/src/server.ts).
 *
 * Events, same names as the Express server:
 *   client → server  "subscribe:route-progress"   (spaceId)
 *   client → server  "unsubscribe:route-progress" (spaceId)
 *   server → client  "route-progress"             (RouteProgressResponseDTO)
 *
 * netty-socketio can't share Tomcat's port, so it listens on loopback
 * SOCKETIO_PORT (default 3001) and SocketIoProxyFilter forwards
 * /socket.io/* from the main port to it. The frontend keeps calling
 * io(API_URL) on port 3000 exactly like it did against Express.
 *
 * Auth replays the REST checks on the handshake: signed `token` cookie →
 * JWT verify. Unauthenticated sockets are disconnected immediately.
 */
@Component
@ConditionalOnProperty(prefix = "scout.socket-io", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RouteProgressSocketServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RouteProgressSocketServer.class);
    private static final String USER_ID_KEY = "userId";

    private final SocketIOServer server;
    private final SignedCookies cookies;
    private final JwtUtil jwt;
    private final SpaceService spaceService;
    private final RouteProgressService progressService;
    private final ExecutorService executor;
    private volatile boolean running = false;

    public RouteProgressSocketServer(
            AppProperties props,
            SignedCookies cookies,
            JwtUtil jwt,
            SpaceService spaceService,
            RouteProgressService progressService,
            @Qualifier(AsyncConfig.BACKGROUND_EXECUTOR) ExecutorService executor) {
        this.cookies = cookies;
        this.jwt = jwt;
        this.spaceService = spaceService;
        this.progressService = progressService;
        this.executor = executor;

        Configuration config = new Configuration();
        // Loopback only: browsers reach it through SocketIoProxyFilter on the main port.
        config.setHostname("127.0.0.1");
        config.setPort(props.socketIo().port());
        List<String> origins = props.allowedOrigins();
        // A single configured origin is sent as-is; with several, the request's
        // Origin is reflected (netty-socketio supports only one fixed value).
        if (origins.size() == 1) config.setOrigin(origins.get(0));

        this.server = new SocketIOServer(config);
        registerHandlers();
    }

    private static String room(int spaceId) {
        return "route-progress:" + spaceId;
    }

    private void registerHandlers() {
        server.addConnectListener(client -> {
            Optional<AuthUser> user = authenticate(client);
            if (user.isEmpty()) {
                client.disconnect();
                return;
            }
            client.set(USER_ID_KEY, user.get().id());
        });

        server.addEventListener("subscribe:route-progress", Integer.class, (client, spaceId, ack) -> {
            Integer userId = client.get(USER_ID_KEY);
            if (userId == null || spaceId == null) return;

            // DB work off the Netty event loop.
            executor.execute(() -> {
                try {
                    spaceService.getOwnedSpace(spaceId, userId);
                } catch (RuntimeException e) {
                    return; // not this user's space — silently ignore, no snapshot/room join
                }

                client.joinRoom(room(spaceId));

                try {
                    client.sendEvent("route-progress", progressService.getRouteProgress(spaceId));
                } catch (RuntimeException e) {
                    log.error("Error sending initial route progress over socket.io", e);
                }
            });
        });

        server.addEventListener("unsubscribe:route-progress", Integer.class, (client, spaceId, ack) -> {
            if (spaceId != null) client.leaveRoom(room(spaceId));
        });
    }

    private Optional<AuthUser> authenticate(SocketIOClient client) {
        try {
            String cookieHeader = client.getHandshakeData().getHttpHeaders().get("Cookie");
            return cookies.getFromHeader(cookieHeader, "token").map(jwt::verify);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Fan-out of every route_progress write to the space's room. */
    @EventListener
    public void onRouteProgress(RouteProgressEvent event) {
        if (!running) return;
        RouteProgressResponseDTO progress = event.progress();
        server.getRoomOperations(room(event.spaceId())).sendEvent("route-progress", progress);
    }

    @Override
    public void start() {
        server.start();
        running = true;
        log.info("Socket.IO server listening on port {}", server.getConfiguration().getPort());
    }

    @Override
    public void stop() {
        running = false;
        server.stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
