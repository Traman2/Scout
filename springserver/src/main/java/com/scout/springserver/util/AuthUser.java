package com.scout.springserver.util;

/** Decoded JWT payload (AuthTokenPayload in backend/src/utils/jwt.ts) — what `req.user` holds. */
public record AuthUser(int id, String email, String role) {}
