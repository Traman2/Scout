import aj from '#configs/arcjet.ts';
import { slidingWindow } from '@arcjet/node';
import logger from '#configs/logger.ts';
import type { NextFunction, Request, Response } from 'express';

const securityMiddleware = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  try {
    const role = req.user?.role || 'guest';

    let limit;
    switch (role) {
      case 'admin':
        limit = 100;
        break;
      case 'user':
        limit = 50;
        break;
      case 'guest':
        limit = 5;
        break;
      default:
        limit = 5;
    }

    const client = aj.withRule(
      slidingWindow({
        mode: 'LIVE',
        interval: '1m',
        max: limit,
      })
    );

    const decision = await client.protect(req);

    if (decision.isDenied() && decision.reason.isBot()) {
      logger.warn('Bot request blocked', {
        ip: req.ip,
        userAgent: req.get('User-Agent'),
        path: req.path,
      });
      return res.status(403).json({
        error: 'Forbidden',
        message: 'Automated requests are not allowed',
      });
    }
    if (decision.isDenied() && decision.reason.isShield()) {
      logger.warn('Sheild request blocked', {
        ip: req.ip,
        userAgent: req.get('User-Agent'),
        path: req.path,
        method: req.method,
      });
      return res
        .status(403)
        .json({ error: 'Forbidden', message: 'Blocked by security policy' });
    }
    if (decision.isDenied() && decision.reason.isRateLimit()) {
      logger.warn('Rate limit exceeded', {
        ip: req.ip,
        userAgent: req.get('User-Agent'),
        path: req.path,
      });
      return res
        .status(403)
        .json({ error: 'Forbidden', message: 'Too many requests' });
    }

    next();
  } catch (e) {
    logger.error('Arcjet middleware error', e);
    res.status(500).json({
      error: 'Internal server error',
      message: 'Something went wrong with security middleware',
    });
  }
};

export default securityMiddleware;
