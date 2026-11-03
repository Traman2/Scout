import axios from 'axios';
import logger from '#configs/logger.ts';
import {
  captureAnalyticsEvent,
  getAnalyticsErrorCode,
} from '#configs/analytics.ts';
import {
  handleSpaceControllerError,
  parseSpaceId,
} from '#controllers/space-controller.utils.ts';
import {
  DEFAULT_POI_RADIUS_METERS,
  discoverPoisForSpace,
  listPoisForSpace,
} from '#services/poi.service.ts';
import { getSpaceForUser } from '#services/space.service.ts';
import { formatValidationError } from '#utils/format.ts';
import { discoverPoisSchema } from '#validations/space.validations.ts';
import { type NextFunction, type Request, type Response } from 'express';

const elapsedMilliseconds = (startedAt: number): number =>
  Math.max(0, Math.round(performance.now() - startedAt));

export const discoverPois = async (req: Request, res: Response) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  const bodyResult = discoverPoisSchema.safeParse(req.body ?? {});

  if (!bodyResult.success) {
    return res.status(400).json({
      error: 'Validation failed',
      details: formatValidationError(bodyResult.error),
    });
  }

  try {
    const ownerId = req.user!.id;
    const space = await getSpaceForUser({ id: spaceId, ownerId });

    if (space.lat === null || space.lng === null) {
      return res.status(422).json({
        error: 'Missing coordinates',
        message: 'Geocode this space before discovering nearby POIs',
      });
    }

    const radiusMeters =
      bodyResult.data.radiusMeters ?? DEFAULT_POI_RADIUS_METERS;
    const startedAt = performance.now();
    let pois;

    try {
      pois = await discoverPoisForSpace({
        spaceId: space.id,
        lat: space.lat,
        lng: space.lng,
        radiusMeters,
      });
    } catch (error) {
      captureAnalyticsEvent(ownerId, 'poi_discovery_failed', {
        space_id: space.id,
        error_code: getAnalyticsErrorCode(error),
        duration_ms: elapsedMilliseconds(startedAt),
      });
      throw error;
    }

    captureAnalyticsEvent(ownerId, 'poi_discovery_completed', {
      space_id: space.id,
      poi_count: pois.length,
      radius_meters: radiusMeters,
      duration_ms: elapsedMilliseconds(startedAt),
    });

    res.status(200).json({ message: 'POIs discovered', pois });
  } catch (error) {
    if (error instanceof Error && error.message === 'Space not found') {
      return res.status(404).json({ error: 'Space not found' });
    }

    const code = axios.isAxiosError(error) ? error.code : undefined;
    logger.error('Discover POIs error', {
      code,
      message: error instanceof Error ? error.message : 'Unknown error',
      spaceId,
    });

    if (code === 'ECONNABORTED' || code === 'ETIMEDOUT') {
      return res.status(504).json({
        error: 'POI discovery timed out',
        message:
          'The nearby places service timed out. Please try again shortly.',
        retryable: true,
      });
    }

    return res.status(500).json({ error: 'Internal server error' });
  }
};

export const listPois = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  try {
    const ownerId = req.user!.id;
    const space = await getSpaceForUser({ id: spaceId, ownerId });
    const pois = await listPoisForSpace(space.id);

    res.status(200).json({ pois });
  } catch (error) {
    return handleSpaceControllerError(error, res, next, 'List POIs error');
  }
};
