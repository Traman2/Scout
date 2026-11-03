---
name: add-feature
description: Add a feature, route, page, or API endpoint to this SaaS repository's Express/TypeScript backend and React/Vite frontend. Use for deciding whether behavior is public or user-scoped and implementing it across the repository's established layers.
---

# Add a feature

This repository has an Express 5 + TypeScript API in `backend/` and a React + Vite app in `frontend/`. The API uses Drizzle/Postgres, cookie-based JWT authentication, and Arcjet security middleware. The apps run on separate origins in development: `http://localhost:5173` calls `http://localhost:3000`.

Read `backend/ARCHITECTURE.md` before changing the request pipeline, middleware order, authentication model, configuration, or secrets. Treat it as the backend source of truth.

## Choose the access model

- Put user-scoped behavior, including anything tied to `req.user.id`, behind `requireAuth`.
- Leave behavior needed by anonymous visitors public. It still passes through global Helmet, CORS, Arcjet shield, and rate-limit middleware.

## Implement the backend

Follow the existing `auth.*` feature and use the layers that the feature needs:

```text
src/models/<feature>.model.ts             Drizzle table schema; omit if no table is needed
src/validations/<feature>.validations.ts  Zod request schema
src/services/<feature>.service.ts         Business logic and database access; no req/res
src/controllers/<feature>.controller.ts  Request parsing, validation, service call, response
src/routes/<feature>.routes.ts            Router and requireAuth for user-scoped routes
```

Mount the router in `src/app.ts`:

```ts
app.use('/api/<feature>', featureRoutes);
```

Preserve these invariants:

- Derive ownership from `req.user.id`; never trust a client-supplied owner or user ID.
- Use the `#models/*`, `#services/*`, `#controllers/*`, and other subpath imports defined in `backend/package.json` for cross-layer imports.
- Treat caught values as `unknown` and narrow them before reading properties.
- Log through `#configs/logger.ts`, never `console.*`.
- For a new table, run `npm run db:generate` and then `npm run db:migrate`. Never edit an already-applied migration in `backend/drizzle/`.
- Fail fast when required configuration is absent; do not provide insecure fallback secrets.

From `backend/`, run `npx tsc --noEmit` and `npx eslint .` before considering the backend complete.

## Connect the frontend

Use `$connect-backend` for the shared API client, authentication state, and cookie/CORS behavior. Public and authenticated calls both use `credentials: 'include'`.

Verify that:

- the frontend origin is included in the backend's comma-separated `CORS_ORIGIN`;
- authenticated routes derive ownership from the authenticated request;
- new tables have generated and applied migrations;
- a 401 is treated as a missing or expired session, not a generic failure;
- relevant backend and frontend checks pass.

When changing the backend pipeline or authentication model, update `backend/ARCHITECTURE.md` and then update this skill if the feature workflow changed.
