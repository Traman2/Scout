import logger from '#configs/logger.ts';
import jwt, { type JwtPayload } from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error('JWT_SECRET environment variable is required');
}
const JWT_EXPIRES_IN = '1d';

export interface AuthTokenPayload extends JwtPayload {
  id: number;
  email: string;
  role: string;
}

export const jwttoken = {
  sign: (payload: { id: number; email: string; role: string }) => {
    try {
      return jwt.sign(payload, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN });
    } catch (e) {
      logger.error('Failed to authenticate token', e);
      throw new Error('Failed to authenticate token', { cause: e });
    }
  },
  verify: (token: string): AuthTokenPayload => {
    try {
      return jwt.verify(token, JWT_SECRET, {
        algorithms: ['HS256'],
      }) as AuthTokenPayload;
    } catch (e) {
      logger.error('Failed to authenticate token', e);
      throw new Error('Failed to authenticate token', { cause: e });
    }
  },
};
