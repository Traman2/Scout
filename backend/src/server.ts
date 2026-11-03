import http from 'http';
import cookieParser from 'cookie-parser';
import { Server as SocketIOServer, type DefaultEventsMap } from 'socket.io';
import app, { allowedOrigins } from './app.ts';
import { shutdownAnalytics } from '#configs/analytics.ts';
import logger from '#configs/logger.ts';
import { jwttoken } from '#utils/jwt.ts';
import { cookies } from '#utils/cookies.ts';
import { getSpaceForUser } from '#services/space.service.ts';
import {
  getRouteProgress,
  routeProgressEvents,
  type RouteProgressSnapshot,
} from '#services/route-progress.service.ts';
import type { Request } from 'express';

interface RouteProgressSocketData {
  userId: number;
}

const PORT = process.env.PORT || 3000;

const server = http.createServer(app);

// Same HTTP server/port as the REST API — Socket.IO's own handshake
// (default path /socket.io) sits alongside Express's routes rather than
// needing a separate server/port.
const io = new SocketIOServer<
  DefaultEventsMap,
  DefaultEventsMap,
  DefaultEventsMap,
  RouteProgressSocketData
>(server, {
  cors: { origin: allowedOrigins, credentials: true },
});

// Socket.IO handshakes bypass the normal Express middleware stack, so auth
// here manually replays the same cookie-parse + JWT-verify steps requireAuth
// runs on ordinary requests (backend/src/middleware/auth.middleware.ts).
io.use((socket, next) => {
  cookieParser(process.env.COOKIE_SECRET)(
    socket.request as unknown as Request,
    {} as never,
    err => {
      if (err) return next(new Error('Unauthorized'));

      const token = cookies.get(socket.request as unknown as Request, 'token');
      if (!token) return next(new Error('Unauthorized'));

      try {
        socket.data.userId = jwttoken.verify(token).id;
        next();
      } catch {
        next(new Error('Unauthorized'));
      }
    }
  );
});

const routeProgressRoom = (spaceId: number) => `route-progress:${spaceId}`;

io.on('connection', socket => {
  socket.on('subscribe:route-progress', (spaceId: number) => {
    void (async () => {
      try {
        await getSpaceForUser({ id: spaceId, ownerId: socket.data.userId });
      } catch {
        return; // not this user's space — silently ignore, no snapshot/room join
      }

      await socket.join(routeProgressRoom(spaceId));

      try {
        const snapshot = await getRouteProgress(spaceId);
        socket.emit('route-progress', snapshot);
      } catch (e) {
        logger.error('Error sending initial route progress over socket.io', e);
      }
    })();
  });

  socket.on('unsubscribe:route-progress', (spaceId: number) => {
    void socket.leave(routeProgressRoom(spaceId));
  });
});

routeProgressEvents.on(
  'progress',
  (spaceId: number, snapshot: RouteProgressSnapshot) => {
    io.to(routeProgressRoom(spaceId)).emit('route-progress', snapshot);
  }
);

server.listen(PORT, () => {
  logger.info(`This server is running on http://localhost:${PORT}`);
});

let isShuttingDown = false;

const shutdown = async (signal: NodeJS.Signals) => {
  if (isShuttingDown) return;
  isShuttingDown = true;

  logger.info(`Received ${signal}; shutting down`);

  try {
    await io.close();
  } catch (e) {
    logger.error('Failed to close the HTTP and Socket.IO server cleanly', e);
    process.exitCode = 1;
  }

  try {
    await shutdownAnalytics();
  } catch (e) {
    logger.error('Failed to flush analytics during shutdown', e);
    process.exitCode = 1;
  }
};

process.once('SIGINT', () => {
  void shutdown('SIGINT');
});

process.once('SIGTERM', () => {
  void shutdown('SIGTERM');
});
