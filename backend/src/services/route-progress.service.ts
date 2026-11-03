import logger from '#configs/logger.ts';
import { db } from '#configs/database.ts';
import { routeProgress } from '#models/route-progress.model.ts';
import { eq } from 'drizzle-orm';
import { EventEmitter } from 'node:events';

export type RouteProgressStatus = 'idle' | 'in_progress' | 'done' | 'error';

export interface RouteProgressSnapshot {
  status: RouteProgressStatus;
  completed: number;
  total: number;
  error: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

// Relays every DB write to server.ts, which re-broadcasts it to the
// Socket.IO room for that space — the DB row remains the durable source of
// truth, this is just the live fan-out so subscribers don't have to poll.
// A single 'progress' event (rather than one per spaceId) is enough since
// Socket.IO rooms already handle the per-space fan-out to subscribers.
export const routeProgressEvents = new EventEmitter();

const idleSnapshot = (): RouteProgressSnapshot => ({
  status: 'idle',
  completed: 0,
  total: 0,
  error: null,
  startedAt: null,
  finishedAt: null,
});

const toSnapshot = (
  row: typeof routeProgress.$inferSelect
): RouteProgressSnapshot => ({
  status: row.status as RouteProgressStatus,
  completed: row.completed,
  total: row.total,
  error: row.error,
  startedAt: row.started_at.toISOString(),
  finishedAt: row.finished_at ? row.finished_at.toISOString() : null,
});

const emitProgress = (spaceId: number, snapshot: RouteProgressSnapshot) => {
  routeProgressEvents.emit('progress', spaceId, snapshot);
};

export const getRouteProgress = async (
  spaceId: number
): Promise<RouteProgressSnapshot> => {
  try {
    const [row] = await db
      .select()
      .from(routeProgress)
      .where(eq(routeProgress.spaceId, spaceId))
      .limit(1);

    return row ? toSnapshot(row) : idleSnapshot();
  } catch (e) {
    logger.error('Error fetching route progress', e);
    throw e;
  }
};

export const startRouteProgress = async (
  spaceId: number,
  total: number
): Promise<RouteProgressSnapshot> => {
  try {
    const now = new Date();
    const [row] = await db
      .insert(routeProgress)
      .values({
        spaceId,
        status: 'in_progress',
        completed: 0,
        total,
        error: null,
        started_at: now,
        finished_at: null,
        updated_at: now,
      })
      .onConflictDoUpdate({
        target: routeProgress.spaceId,
        set: {
          status: 'in_progress',
          completed: 0,
          total,
          error: null,
          started_at: now,
          finished_at: null,
          updated_at: now,
        },
      })
      .returning();

    const snapshot = toSnapshot(row);
    emitProgress(spaceId, snapshot);
    return snapshot;
  } catch (e) {
    logger.error('Error starting route progress', e);
    throw e;
  }
};

export const updateRouteProgress = async (
  spaceId: number,
  completed: number,
  total: number
): Promise<void> => {
  try {
    const [row] = await db
      .update(routeProgress)
      .set({ completed, total, updated_at: new Date() })
      .where(eq(routeProgress.spaceId, spaceId))
      .returning();

    if (!row) return;
    emitProgress(spaceId, toSnapshot(row));
  } catch (e) {
    logger.error('Error updating route progress', e);
  }
};

export const completeRouteProgress = async (
  spaceId: number
): Promise<RouteProgressSnapshot | null> => {
  try {
    const [row] = await db
      .update(routeProgress)
      .set({ status: 'done', finished_at: new Date(), updated_at: new Date() })
      .where(eq(routeProgress.spaceId, spaceId))
      .returning();

    if (!row) return null;
    const snapshot = toSnapshot(row);
    emitProgress(spaceId, snapshot);
    return snapshot;
  } catch (e) {
    logger.error('Error completing route progress', e);
    return null;
  }
};

export const failRouteProgress = async (
  spaceId: number,
  error: string
): Promise<RouteProgressSnapshot | null> => {
  try {
    const [row] = await db
      .update(routeProgress)
      .set({
        status: 'error',
        error,
        finished_at: new Date(),
        updated_at: new Date(),
      })
      .where(eq(routeProgress.spaceId, spaceId))
      .returning();

    if (!row) return null;
    const snapshot = toSnapshot(row);
    emitProgress(spaceId, snapshot);
    return snapshot;
  } catch (e) {
    logger.error('Error failing route progress', e);
    return null;
  }
};
