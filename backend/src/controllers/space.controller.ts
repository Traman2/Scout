import {
  captureAnalyticsEvent,
  getAnalyticsErrorCode,
} from '#configs/analytics.ts';
import logger from '#configs/logger.ts';
import {
  handleSpaceControllerError,
  parseSpaceId,
} from '#controllers/space-controller.utils.ts';
import {
  createSpace as createSpaceService,
  deleteSpaceForUser,
  geocodeSpace as geocodeSpaceService,
  getSpaceForUser,
  listSpacesForUser,
  type GeocodeAttempt,
  updateSpaceForUser,
} from '#services/space.service.ts';
import { formatValidationError } from '#utils/format.ts';
import {
  createSpaceSchema,
  updateSpaceSchema,
} from '#validations/space.validations.ts';
import { type NextFunction, type Request, type Response } from 'express';

const captureAutomaticGeocodeAttempt = (
  ownerId: number,
  spaceId: number,
  geocodeAttempt: GeocodeAttempt | null
) => {
  if (!geocodeAttempt) return;

  if (geocodeAttempt.status === 'succeeded') {
    captureAnalyticsEvent(ownerId, 'space_geocoded', {
      space_id: spaceId,
    });
    return;
  }

  captureAnalyticsEvent(ownerId, 'space_geocode_failed', {
    space_id: spaceId,
    error_code: getAnalyticsErrorCode(geocodeAttempt.error),
  });
};

const captureCreatedSpace = async (
  ownerId: number,
  space: { id: number },
  geocodeAttempt: GeocodeAttempt
) => {
  let spaceOrdinal: number | null = null;

  try {
    spaceOrdinal = (await listSpacesForUser(ownerId)).length;
  } catch {
    logger.warn('Unable to calculate space ordinal for analytics');
  }

  captureAnalyticsEvent(ownerId, 'space_created', {
    space_id: space.id,
    space_ordinal: spaceOrdinal,
    is_first_space: spaceOrdinal === null ? null : spaceOrdinal === 1,
  });

  captureAutomaticGeocodeAttempt(ownerId, space.id, geocodeAttempt);
};

export const createSpace = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const validationResult = createSpaceSchema.safeParse(req.body);

  if (!validationResult.success) {
    return res.status(400).json({
      error: 'Validation failed',
      details: formatValidationError(validationResult.error),
    });
  }

  try {
    const { name, address } = validationResult.data;
    const ownerId = req.user!.id;
    const { space, geocodeAttempt } = await createSpaceService({
      ownerId,
      name,
      address,
    });

    res.status(201).json({ message: 'Space created', space });
    void captureCreatedSpace(ownerId, space, geocodeAttempt);
  } catch (error) {
    logger.error('Create space error', error);
    next(error);
  }
};

export const listSpaces = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  try {
    const spaces = await listSpacesForUser(req.user!.id);
    res.status(200).json({ spaces });
  } catch (error) {
    logger.error('List spaces error', error);
    next(error);
  }
};

export const getSpace = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  try {
    const space = await getSpaceForUser({
      id: spaceId,
      ownerId: req.user!.id,
    });

    res.status(200).json({ space });
  } catch (error) {
    return handleSpaceControllerError(error, res, next, 'Get space error');
  }
};

export const updateSpace = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  const bodyResult = updateSpaceSchema.safeParse(req.body);

  if (!bodyResult.success) {
    return res.status(400).json({
      error: 'Validation failed',
      details: formatValidationError(bodyResult.error),
    });
  }

  try {
    const { space, geocodeAttempt } = await updateSpaceForUser({
      id: spaceId,
      ownerId: req.user!.id,
      data: bodyResult.data,
    });

    captureAutomaticGeocodeAttempt(req.user!.id, space.id, geocodeAttempt);

    res.status(200).json({ message: 'Space updated', space });
  } catch (error) {
    return handleSpaceControllerError(error, res, next, 'Update space error');
  }
};

export const geocodeSpace = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  const ownerId = req.user!.id;

  try {
    let space;

    try {
      space = await geocodeSpaceService({ id: spaceId, ownerId });
    } catch (error) {
      if (!(error instanceof Error && error.message === 'Space not found')) {
        captureAnalyticsEvent(ownerId, 'space_geocode_failed', {
          space_id: spaceId,
          error_code: getAnalyticsErrorCode(error),
        });
      }
      throw error;
    }

    captureAnalyticsEvent(ownerId, 'space_geocoded', {
      space_id: spaceId,
    });

    res.status(200).json({ message: 'Space geocoded', space });
  } catch (error) {
    if (error instanceof Error && error.message === 'Address not found') {
      return res.status(422).json({
        error: 'Address not found',
        message: 'Could not find coordinates for this address',
      });
    }

    return handleSpaceControllerError(error, res, next, 'Geocode space error');
  }
};

export const deleteSpace = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const spaceId = parseSpaceId(req, res);
  if (spaceId === null) return;

  try {
    await deleteSpaceForUser({ id: spaceId, ownerId: req.user!.id });
    res.status(200).json({ message: 'Space deleted' });
  } catch (error) {
    return handleSpaceControllerError(error, res, next, 'Delete space error');
  }
};
