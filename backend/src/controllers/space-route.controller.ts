import {
  captureAnalyticsEvent,
  getAnalyticsErrorCode,
} from '#configs/analytics.ts';
import logger from '#configs/logger.ts';
import {
  handleSpaceControllerError,
  parseSpaceId,
} from '#controllers/space-controller.utils.ts';
import { listPoisForSpace } from '#services/poi.service.ts';
import {
  completeRouteProgress,
  failRouteProgress,
  getRouteProgress,
  startRouteProgress,
  updateRouteProgress,
} from '#services/route-progress.service.ts';
import {
  computeRoutesForSpace,
  listRoutesForSpace,
} from '#services/routing.service.ts';
import { getSpaceForUser } from '#services/space.service.ts';
import { type NextFunction, type Request, type Response } from 'express';

const durationBetween = (startedAt: string, finishedAt: string): number => {
  const duration = Date.parse(finishedAt) - Date.parse(startedAt);
  return Number.isFinite(duration) ? Math.max(0, duration) : 0;
};

export const computeRoutes = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  try {
    const ownerId = req.user!.id;
    const space = await getSpaceForUser({ id: spaceId, ownerId });

    if (space.lat === null || space.lng === null) {
      return res.status(422).json({
        error: 'Missing coordinates',
        message: 'Geocode this space before computing routes',
      });
    }

    const pois = await listPoisForSpace(space.id);

    if (pois.length === 0) {
      return res.status(422).json({
        error: 'No POIs',
        message: 'Discover POIs before computing routes',
      });
    }

    const existing = await getRouteProgress(space.id);

    if (existing.status === 'in_progress') {
      return res.status(200).json({
        message: 'Route computation already in progress',
        progress: existing,
      });
    }

    const progress = await startRouteProgress(space.id, pois.length);
    const jobStartedAt = progress.startedAt ?? new Date().toISOString();

    captureAnalyticsEvent(ownerId, 'route_computation_started', {
      space_id: space.id,
      poi_count: pois.length,
      job_started_at: jobStartedAt,
    });

    res.status(202).json({ message: 'Route computation started', progress });

    // The job outlives the HTTP response. Durable progress remains the source
    // of truth, with terminal analytics emitted only after that state updates.
    let completedCount = 0;

    void computeRoutesForSpace({
      spaceId: space.id,
      spaceLat: space.lat,
      spaceLng: space.lng,
      pois,
      onBatchProgress: (completed, total) => {
        completedCount = completed;
        return updateRouteProgress(space.id, completed, total);
      },
    })
      .then(async routes => {
        const terminalProgress = await completeRouteProgress(space.id);
        if (!terminalProgress?.finishedAt) return;

        captureAnalyticsEvent(ownerId, 'route_computation_completed', {
          space_id: space.id,
          poi_count: pois.length,
          route_count: routes.length,
          duration_ms: durationBetween(
            jobStartedAt,
            terminalProgress.finishedAt
          ),
        });
      })
      .catch(async error => {
        logger.error('Compute routes error', error);
        const terminalProgress = await failRouteProgress(
          space.id,
          error instanceof Error ? error.message : 'Unknown error'
        );
        if (!terminalProgress?.finishedAt) return;

        captureAnalyticsEvent(ownerId, 'route_computation_failed', {
          space_id: space.id,
          poi_count: pois.length,
          completed_count: completedCount,
          duration_ms: durationBetween(
            jobStartedAt,
            terminalProgress.finishedAt
          ),
          error_code: getAnalyticsErrorCode(error),
        });
      });
  } catch (error) {
    return handleSpaceControllerError(error, res, next, 'Compute routes error');
  }
};

export const getRouteProgressHandler = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  try {
    const ownerId = req.user!.id;
    await getSpaceForUser({ id: spaceId, ownerId });
    const progress = await getRouteProgress(spaceId);

    res.status(200).json({ progress });
  } catch (error) {
    return handleSpaceControllerError(
      error,
      res,
      next,
      'Get route progress error'
    );
  }
};

export const listRoutes = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  try {
    const ownerId = req.user!.id;
    const space = await getSpaceForUser({ id: spaceId, ownerId });
    const routes = await listRoutesForSpace(space.id);

    res.status(200).json({ routes });
  } catch (error) {
    return handleSpaceControllerError(error, res, next, 'List routes error');
  }
};
