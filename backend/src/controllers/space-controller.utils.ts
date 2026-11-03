import logger from '#configs/logger.ts';
import { formatValidationError } from '#utils/format.ts';
import { spaceIdParamsSchema } from '#validations/space.validations.ts';
import { type NextFunction, type Request, type Response } from 'express';

export const parseSpaceId = (req: Request, res: Response): number | null => {
  const result = spaceIdParamsSchema.safeParse(req.params);

  if (!result.success) {
    res.status(400).json({
      error: 'Validation failed',
      details: formatValidationError(result.error),
    });
    return null;
  }

  return result.data.id;
};

export const handleSpaceControllerError = (
  error: unknown,
  res: Response,
  next: NextFunction,
  logMessage: string
): Response | void => {
  if (error instanceof Error && error.message === 'Space not found') {
    return res.status(404).json({ error: 'Space not found' });
  }

  logger.error(logMessage, error);
  next(error);
};
