import logger from '#configs/logger.ts';
import {
  captureAnalyticsEvent,
  getAnalyticsDistinctId,
} from '#configs/analytics.ts';
import {
  createUser,
  authenticateUser,
  getUserById,
} from '#services/auth.service.js';
import { formatValidationError } from '#utils/format.ts';
import { signupSchema, signInSchema } from '../validations/auth.validations.ts';
import { jwttoken } from '#utils/jwt.js';
import { cookies } from '#utils/cookies.js';
import { type NextFunction, type Request, type Response } from 'express';

export const signup = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  try {
    const validationResult = signupSchema.safeParse(req.body);

    if (!validationResult.success) {
      return res.status(400).json({
        error: 'Validation failed',
        details: formatValidationError(validationResult.error),
      });
    }

    const { name, email, password } = validationResult.data;

    // Call auth service
    const user = await createUser({ name, email, password });

    const token = jwttoken.sign({
      id: user.id,
      email: user.email,
      role: user.role,
    });

    cookies.set(res, 'token', token);

    captureAnalyticsEvent(user.id, 'user_signed_up', { role: user.role });

    logger.info(`User registered successfully ${email}`);

    res.status(201).json({
      message: 'User registered',
      user: {
        id: user.id,
        analyticsId: getAnalyticsDistinctId(user.id),
        name: user.name,
        email: user.email,
        role: user.role,
      },
    });
  } catch (e) {
    logger.error('Signup error', e);

    if (
      e instanceof Error &&
      e.message === 'User with this email already exists'
    ) {
      return res.status(409).json({ error: 'Email already exists' });
    }

    next(e);
  }
};

export const signin = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  try {
    const validationResult = signInSchema.safeParse(req.body);

    if (!validationResult.success) {
      return res.status(400).json({
        error: 'Validation failed',
        details: formatValidationError(validationResult.error),
      });
    }

    const { email, password } = validationResult.data;

    // Call auth service
    const user = await authenticateUser({ email, password });

    const token = jwttoken.sign({
      id: user.id,
      email: user.email,
      role: user.role,
    });

    cookies.set(res, 'token', token);

    captureAnalyticsEvent(user.id, 'user_signed_in', { role: user.role });

    logger.info(`User signed in successfully ${email}`);

    res.status(200).json({
      message: 'User signed in',
      user: {
        id: user.id,
        analyticsId: getAnalyticsDistinctId(user.id),
        name: user.name,
        email: user.email,
        role: user.role,
      },
    });
  } catch (e) {
    logger.error('Signin error', e);

    if (
      e instanceof Error &&
      (e.message === 'User not found' || e.message === 'Invalid password')
    ) {
      return res.status(401).json({ error: 'Invalid email or password' });
    }

    next(e);
  }
};

export const signout = (_req: Request, res: Response, next: NextFunction) => {
  try {
    cookies.clear(res, 'token');

    logger.info('User signed out successfully');

    res.status(200).json({
      message: 'User signed out',
    });
  } catch (e) {
    logger.error('Signout error', e);
    next(e);
  }
};

export const getCurrentUser = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  try {
    const user = await getUserById(req.user!.id);
    res.status(200).json({
      user: {
        ...user,
        analyticsId: getAnalyticsDistinctId(user.id),
      },
    });
  } catch (e) {
    if (e instanceof Error && e.message === 'User not found') {
      return res
        .status(401)
        .json({ error: 'Unauthorized', message: 'User not found' });
    }
    logger.error('Get current user error', e);
    next(e);
  }
};
