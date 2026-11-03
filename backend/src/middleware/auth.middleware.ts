import logger from '#configs/logger.ts';
import { jwttoken } from '#utils/jwt.ts';
import { cookies } from '#utils/cookies.ts';
import type { NextFunction, Request, Response } from 'express';

// Opportunistically populates req.user from the token cookie, if present and
// valid, without blocking the request. Mount before any middleware (e.g.
// rate limiting) that needs to know the caller's role on unauthenticated
// routes. Invalid/missing tokens are ignored here; requireAuth is what
// actually enforces authentication and logs rejected tokens.
export const identifyUser = (
  req: Request,
  _res: Response,
  next: NextFunction
) => {
  const token = cookies.get(req, 'token');

  if (token) {
    try {
      req.user = jwttoken.verify(token);
    } catch {
      // Not an error here — the request just continues unauthenticated.
    }
  }

  next();
};

export const requireAuth = (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const token = cookies.get(req, 'token');

  if (!token) {
    return res
      .status(401)
      .json({ error: 'Unauthorized', message: 'Authentication required' });
  }

  try {
    req.user = jwttoken.verify(token);
    next();
  } catch (e) {
    const message = e instanceof Error ? e.message : 'Unknown error';
    logger.warn('Rejected invalid or expired token', { message });
    res
      .status(401)
      .json({ error: 'Unauthorized', message: 'Invalid or expired token' });
  }
};
