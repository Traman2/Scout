import type { CookieOptions, Request, Response } from 'express';

export const cookies = {
  getOptions: (): CookieOptions => ({
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    // Must match JWT_EXPIRES_IN in utils/jwt.ts — otherwise the cookie
    // disappears client-side well before the token it holds actually
    // expires server-side.
    maxAge: 24 * 60 * 60 * 1000,
  }),

  set: (
    res: Response,
    name: string,
    value: string,
    options: CookieOptions = {}
  ) => {
    res.cookie(name, value, {
      ...cookies.getOptions(),
      signed: true,
      ...options,
    });
  },

  clear: (res: Response, name: string, options: CookieOptions = {}) => {
    res.clearCookie(name, { ...cookies.getOptions(), ...options });
  },

  get: (req: Request, name: string): string | undefined => {
    const value: unknown = req.signedCookies[name];
    return typeof value === 'string' ? value : undefined;
  },
};
