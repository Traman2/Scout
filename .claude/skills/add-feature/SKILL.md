---
name: add-feature
description: "Use when adding a new feature, route, page, or API endpoint to this SaaS product — the Express/TypeScript API in backend/ and the React+Vite app in frontend/. Covers deciding whether a feature is user-scoped (requires auth) or public, the backend layering convention, and the cookie/CORS contract the frontend must follow to call the API."
---

# Adding a feature to this SaaS product

Two halves of one repo: `backend/` (Express 5 + TypeScript + Drizzle/Postgres,
cookie-JWT auth, Arcjet security middleware) and `frontend/` (React + Vite,
separate origin in dev — `http://localhost:5173` talking to the API on
`http://localhost:3000`).

Backend details in this file are load-bearing and kept in sync with the
code. For the full request pipeline, middleware order, and config/secrets
model, read `backend/ARCHITECTURE.md` — this skill is the shorter
"how do I add X" version of that doc, plus the frontend side it doesn't
cover.

## Step 1: is the feature user-scoped or public?

- **User-scoped** (a "spaces" page, billing, anything tied to `req.user.id`)
  → backend route goes behind `requireAuth`.
- **Public** (landing content, pricing, health checks, anything anonymous
  visitors need) → no `requireAuth`, but it still passes through the global
  pipeline (helmet, CORS, Arcjet shield/rate-limit) — "public" doesn't mean
  "unprotected."

This determines which backend recipe below to follow. It doesn't change the
frontend recipe — both kinds of routes are called the same way.

## Backend: adding the route

Follow the existing `auth.*` feature as the template. Four files, one per
layer, named after the feature:

```
src/models/<feature>.model.ts         Drizzle table schema (skip if no new table)
src/validations/<feature>.validations.ts  Zod schema for the request body
src/services/<feature>.service.ts     Business logic + DB queries, no req/res
src/controllers/<feature>.controller.ts  Reads req, validates, calls service, shapes res.json()
src/routes/<feature>.routes.ts        Router; requireAuth here if user-scoped
```

Then mount it in `src/app.ts`, after the existing routes:
```ts
app.use('/api/<feature>', featureRoutes);
```

Rules that matter, not just style:

- **Never trust a client-supplied owner/user id.** Zod strips unknown body
  keys by default, but even for known keys — always derive ownership from
  `req.user.id` (available on any route after the global `identifyUser`
  middleware; guaranteed non-null after `requireAuth`), never from the
  request body.
- **Cross-layer imports use the `#alias/*.ts` subpath imports**
  (`#services/*`, `#controllers/*`, etc. — see `backend/package.json`
  `imports`), not relative paths.
- **`catch (e)` variables are `unknown`** — narrow with `e instanceof Error`
  before reading `.message`.
- **Logging goes through `logger`** (`#configs/logger.ts`), never
  `console.*`.
- **New DB tables**: after editing `src/models/*.ts`, run
  `npm run db:generate` then `npm run db:migrate`. Never hand-edit an
  already-applied migration in `drizzle/`.
- **New required secrets/config**: fail fast at module load
  (`if (!process.env.X) throw new Error(...)`), never `|| 'default'`. See
  `JWT_SECRET`/`COOKIE_SECRET`/`ARCJET_KEY`/`CORS_ORIGIN`/`DATABASE_URL` in
  `backend/ARCHITECTURE.md` for the pattern.
- Before considering the backend half done: `npx tsc --noEmit` and
  `npx eslint .` both clean, run from `backend/`.

## Frontend: calling the API

Use the `connect-backend` skill for the API client pattern, auth session
handling, and the cookie/CORS contract in full — it's the same regardless
of whether the route you just added is public or user-scoped. The one
thing worth repeating here: `requireAuth` vs. no `requireAuth` on the
backend route changes nothing about how the frontend calls it —
`credentials: 'include'` goes on every request either way.

## Checklist

- [ ] Backend: model (if needed) → validation → service → controller →
      routes → mounted in `app.ts`.
- [ ] User-scoped routes: `requireAuth` on the router; ownership derived from
      `req.user.id`, not the body.
- [ ] New DB table: migration generated and applied.
- [ ] `npx tsc --noEmit` and `npx eslint .` clean in `backend/`.
- [ ] Frontend: `credentials: 'include'` on the request.
- [ ] Frontend's origin present in the backend's `CORS_ORIGIN`.
- [ ] 401 handling treats it as an auth redirect, not a generic error toast.

## Keeping this in sync

If you change the backend pipeline (middleware order, new global
middleware, auth model), update `backend/ARCHITECTURE.md` first — it's the
source of truth — then update this skill's summary if the change affects
how a new feature gets added.
