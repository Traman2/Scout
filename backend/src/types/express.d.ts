import type { AuthTokenPayload } from '#utils/jwt.ts';

declare module 'express' {
  interface Request {
    user?: AuthTokenPayload;
  }
}
