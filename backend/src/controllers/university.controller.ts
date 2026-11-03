import logger from '#configs/logger.ts';
import {
  computeUniversityCommutesForUser,
  getUniversityForUser,
  listUniversityCommutesForUser,
  saveUniversityForUser,
} from '#services/university.service.ts';
import { formatValidationError } from '#utils/format.ts';
import { upsertUniversitySchema } from '#validations/university.validations.ts';
import { type NextFunction, type Request, type Response } from 'express';

export const getUniversityDashboard = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  try {
    const ownerId = req.user!.id;
    const [university, commutes] = await Promise.all([
      getUniversityForUser(ownerId),
      listUniversityCommutesForUser(ownerId),
    ]);

    res.status(200).json({ university, commutes });
  } catch (e) {
    logger.error('Get university dashboard error', e);
    next(e);
  }
};

export const saveUniversity = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  try {
    const validationResult = upsertUniversitySchema.safeParse(req.body);
    if (!validationResult.success) {
      return res.status(400).json({
        error: 'Validation failed',
        details: formatValidationError(validationResult.error),
      });
    }

    const university = await saveUniversityForUser({
      ownerId: req.user!.id,
      ...validationResult.data,
    });

    res.status(200).json({ message: 'University saved', university });
  } catch (e) {
    if (e instanceof Error && e.message === 'University address not found') {
      return res.status(422).json({
        error: 'Address not found',
        message: 'Could not find coordinates for this university address',
      });
    }

    logger.error('Save university error', e);
    next(e);
  }
};

export const computeUniversityCommutes = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  try {
    const commutes = await computeUniversityCommutesForUser(req.user!.id);
    res.status(200).json({ message: 'University commutes computed', commutes });
  } catch (e) {
    if (e instanceof Error && e.message === 'University not configured') {
      return res.status(422).json({
        error: 'University not configured',
        message: 'Add your university before calculating commutes',
      });
    }

    if (e instanceof Error && e.message === 'No geocoded spaces') {
      return res.status(422).json({
        error: 'No geocoded spaces',
        message: 'Add or geocode an apartment before calculating commutes',
      });
    }

    logger.error('Compute university commutes error', e);
    next(e);
  }
};
