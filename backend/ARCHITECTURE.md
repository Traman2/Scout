# Architecture

Express 5 + TypeScript (NodeNext ESM, run directly via `tsx`, no build step for
dev). Postgres via Neon's serverless driver + Drizzle ORM. Auth is
cookie-carried JWTs. Arcjet provides shield/bot/rate-limit protection at the
edge of the app.

## Request pipeline

Every request passes through the same pipeline, defined top-to-bottom in
`src/app.ts`:

```
helmet()              → security headers
cors()                → CORS (currently wide open, see Known gaps)
express.json()        → parse JSON bodies
express.urlencoded()  → parse form bodies
cookieParser(secret)  → parse + verify signed cookies → req.cookies / req.signedCookies
morgan                → access logging, piped into winston
identifyUser           → best-effort: if a valid `token` cookie exists, set req.user
securityMiddleware     → Arcjet: shield, bot detection, role-aware rate limit
  ↓
route routers (mounted per feature, e.g. app.use('/api/auth', authRoutes))
```

**Order matters.** `identifyUser` must run before `securityMiddleware`,
because the rate-limit tier is chosen from `req.user?.role`. If you add
another middleware that needs to know who's calling, mount it after
`identifyUser` too. `securityMiddleware` must run before route routers so
nothing skips it.

Route-level middleware (`requireAuth`, per-route validation, etc.) runs
_after_ this global pipeline, inside each router.

## Layering convention

Each feature follows the same four-layer shape, one file per layer, all
named after the feature (`auth.*`):

```
routes/<feature>.routes.ts        Router: wires HTTP verb + path → controller,
                                   attaches route-level middleware (requireAuth, etc.)
controllers/<feature>.controller.ts  Express-facing: reads req, validates input,
                                      calls services, shapes the res.json()
services/<feature>.service.ts     Business logic + DB access. No req/res here.
models/<feature>.model.ts         Drizzle table schema.
validations/<feature>.validations.ts  Zod schemas for request bodies.
```

Controllers never touch Drizzle directly, and services never touch
`req`/`res`. That boundary is what makes services reusable outside HTTP later
(a cron job, a CLI script, a second protocol) without dragging Express along.

Large features may keep one router while splitting controllers by coherent
sub-resource. The spaces router, for example, composes `space.controller.ts`
for space CRUD/geocoding, `space-poi.controller.ts` for nearby POIs, and
`space-route.controller.ts` for route jobs and cached routes. Shared
controller helpers may centralize HTTP-only validation/error shaping, but
business logic and database access still belong in services.

## Import aliases

`package.json`'s `imports` field defines Node subpath imports, resolved by
both `tsx` at runtime and `tsc`/ESLint at check time:

```
#clients/*      → src/clients/*
#configs/*      → src/configs/*
#controllers/*  → src/controllers/*
#middleware/*   → src/middleware/*
#models/*       → src/models/*
#routes/*       → src/routes/*
#services/*     → src/services/*
#utils/*        → src/utils/*
#validations/*  → src/validations/*
```

Always import cross-layer files through these aliases, not relative paths
(`../../services/...`) — the existing code is inconsistent about this in a
couple of spots (some historical relative imports crept in), don't copy that.
The extension you use (`.ts` vs `.js`) in the specifier doesn't matter
functionally — both resolve to the same `.ts` file — but pick `.ts` for new
code since that's what the file actually is.

## Auth model

- On sign-up/sign-in, `jwttoken.sign({ id, email, role })` (`src/utils/jwt.ts`)
  issues an HS256 JWT, and `cookies.set(res, 'token', token)`
  (`src/utils/cookies.ts`) stores it as an `httpOnly`, `sameSite: 'strict'`,
  **signed** cookie (signed via `cookieParser(COOKIE_SECRET)`, on top of the
  JWT's own signature — a forged/tampered cookie value is rejected before it
  ever reaches `jwt.verify`).
- `req.user` is typed via module augmentation in `src/types/express.d.ts`
  (`user?: AuthTokenPayload`), so any file that imports `express`'s `Request`
  type sees `req.user` without extra casting.
- Two middleware read that cookie, for different purposes:
  - `identifyUser` (global, non-blocking) — populates `req.user` if a valid
    token is present, otherwise silently continues as a guest. Used so
    unauthenticated routes can still know "who, if anyone, is asking"
    (currently: rate-limit tiering).
  - `requireAuth` (route-level, blocking) — same decode, but returns 401 and
    logs a warning if the token is missing or invalid. Use this on any route
    that must not be reachable without a valid session.

Both live in `src/middleware/auth.middleware.ts` and are intentionally
separate functions rather than one shared one, because their failure
behavior is opposite (block vs. pass through) — see the comment in that file.

## Security middleware (Arcjet)

`src/configs/arcjet.ts` defines three rules, all `LIVE` except bot detection:

- `shield` — blocks common attack patterns (SQLi, etc.), always `LIVE`.
- `detectBot` — `LIVE` (blocks) only when `NODE_ENV === 'production'`,
  `DRY_RUN` (logs only) otherwise, so local tools (Postman, curl) aren't
  blocked in dev.
- `slidingWindow` (base, 5 req / 2s) — a global floor.

`src/middleware/security.middleware.ts` additionally layers a **per-role**
sliding-window rate limit (`guest`: 5/min, `user`: 10/min, `admin`: 20/min),
built via `aj.withRule(...)` per request. This only works because
`identifyUser` already ran and populated `req.user` — if you ever reorder the
pipeline, this silently degrades to everyone getting the `guest` tier (this
happened once already; don't reintroduce it).

## Config & secrets

Every secret required for the app to function correctly **fails fast at
import time** rather than falling back to an insecure default:

- `JWT_SECRET` — `src/utils/jwt.ts` throws if unset.
- `COOKIE_SECRET` — `src/app.ts` throws if unset.
- `ARCJET_KEY` — `src/configs/arcjet.ts` throws if unset.
- `CORS_ORIGIN` — `src/app.ts` throws if unset. Comma-separated list of
  allowed origins, passed to `cors({ origin, credentials: true })`. Locally
  this is your frontend dev server's origin (e.g. `http://localhost:5173`);
  add production/staging domains here as they exist, comma-separated.
- `DATABASE_URL` — throws if unset, both for migrations (`drizzle.config.ts`)
  and at runtime (`src/configs/database.ts`).

When adding a new required secret, follow the same pattern: read it once at
module load, throw immediately if missing. Don't add another `|| 'default'`
fallback — that's the exact bug class this codebase had and fixed.

All env vars are documented in `.env.example`: `PORT`, `NODE_ENV`, `LOG_LEVEL`, `DATABASE_URL`,
`ARCJET_ENV`, `ARCJET_KEY`, `JWT_SECRET`, `COOKIE_SECRET`, `CORS_ORIGIN`,
`OSM_USER_AGENT` (optional — see Geocoding and POI discovery below).

PostHog product analytics is optional and controlled by `POSTHOG_ENABLED`.
When enabled, `POSTHOG_PROJECT_TOKEN` and `POSTHOG_HOST` are required and the
process fails fast if either is missing. `src/configs/analytics.ts` owns the
Node SDK and exposes the only capture/shutdown helpers; analytics must never
change an API response or background-job outcome. The server flushes queued
events on `SIGINT` and `SIGTERM`. PostHog never receives the numeric database
user ID. The backend derives a stable HMAC pseudonym and returns that same
`analyticsId` in authenticated user responses so browser and server events can
still be joined. `POSTHOG_IDENTITY_SECRET` may provide a dedicated HMAC secret;
when it is unset, the already-required `JWT_SECRET` is reused with a
PostHog-specific domain prefix.

## Database

Drizzle ORM against Neon Postgres (`@neondatabase/serverless`, HTTP driver —
no persistent connection pool to manage). Schema files live in
`src/models/*.ts`; `drizzle.config.ts` points `drizzle-kit` at that glob.

```
npm run db:generate   # diff schema/*.ts against drizzle/meta, write a new migration
npm run db:migrate    # apply pending migrations to DATABASE_URL
npm run db:studio     # local Drizzle Studio UI
```

Migrations are checked into `drizzle/`. Never edit an already-applied
migration file — add a new one.

## Geocoding

`src/clients/nominatim.client.ts` wraps Nominatim's `/search` endpoint
(`geocodeAddress(address)` → `{ lat, lng } | null`). It's the only place
Nominatim is called from, and self-throttles module-wide to their 1 req/sec
limit via a last-request timestamp — no queue needed since there's a single
call site. Set `OSM_USER_AGENT` (real contact info, per their usage policy;
shared with the Overpass client below) — it falls back to a generic string
otherwise, which risks being rate-limited/blocked beyond light dev use.

`space.service.ts` calls it in two places, both **best-effort** (a
geocoding failure never blocks the space write — `lat`/`lng` are just left
null and can be retried):

- `createSpace` — geocodes the address up front, on creation.
- `updateSpaceForUser` — re-geocodes only when `address` is part of the
  update payload; other field updates leave existing coordinates alone.

`geocodeSpace(id, ownerId)` is a separate service function for a manual
retry (`POST /api/spaces/:id/geocode`) — use it when the initial geocode
failed (transient Nominatim error) or found no match. It throws
`'Address not found'` (→ 422) if Nominatim still can't resolve the address,
distinct from `'Space not found'` (→ 404).

## POI discovery

`src/clients/overpass.client.ts` wraps the Overpass API (`findNearbyPois(lat,
lng, radiusMeters)` → `OverpassPoi[]`), same shape as the Nominatim client:
single call site, module-level throttle (1 req/sec — Overpass's public
instance discourages tight polling), shares `OSM_USER_AGENT`.

Categories are a **fixed, hardcoded** OSM tag mapping in the client
(`CATEGORY_FILTERS`) — grocery, gym, transit — not user-configurable yet
(v2 concern). Only elements with a `name` tag are kept, which is the
quality filter: it excludes anonymous/loosely-tagged nodes.

POIs are **shared across all spaces**, not owned by one — `pois` is a
global table deduped by `osmId` (unique constraint), and `space_pois` is a
many-to-many join table (`space_id`, `poi_id`, unique on the pair) recording
which POIs are "nearby" for which space. The same supermarket found near
two different spaces is one `pois` row referenced by two `space_pois` rows,
not two duplicate POI rows.

`poi.service.ts`'s `discoverPoisForSpace`:

1. Queries Overpass for the space's current `lat`/`lng`/`radiusMeters`.
2. **Upserts** the results into `pois` (`onConflictDoUpdate` on `osmId` —
   inserts new OSM nodes, refreshes `name`/`category`/`lat`/`lng` on ones
   already known from another space's discover).
3. **Deletes and replaces** only this space's rows in `space_pois` — the
   shared `pois` rows themselves are never deleted here, since another
   space may still reference them.

Not atomic (the neon-http driver has no transaction support — see Database
above): the upsert into `pois` is a single statement so that part is safe,
but an interrupted request between the `space_pois` delete and re-insert
can leave a space with zero linked POIs until the next discover; acceptable
for v1. `radiusMeters` is adjustable per request (validated 100–5000,
default `DEFAULT_POI_RADIUS_METERS` = 1500).

`discoverPois` (`POST /api/spaces/:id/discover`) 422s if the space has no
`lat`/`lng` yet — geocode it first. `listPois` (`GET /api/spaces/:id/pois`)
joins `space_pois` → `pois` and reads back the cached rows without
re-querying Overpass.

## Transit routing

`src/clients/otp.client.ts` wraps a self-hosted OpenTripPlanner (OTP)
instance's **GTFS GraphQL API** (`POST {OTP_URL}/otp/gtfs/v1`) — OTP2's older
REST `/plan` endpoint was removed upstream, so this is GraphQL-only.
`planTrip(from, to)` → `{ durationSeconds, walkDurationSeconds,
metroDurationSeconds, busDurationSeconds, tramDurationSeconds,
otherDurationSeconds, waitDurationSeconds } | null`, `null` meaning OTP found
no itinerary (same "no result" convention as `geocodeAddress`). Mode buckets
follow `MODE_BUCKETS` in `otp.client.ts` — a fixed mapping (same "v1: fixed,
not user-configurable" approach as `CATEGORY_FILTERS` in
`overpass.client.ts`) from OTP's `leg.mode` to one of Madrid's three loaded
GTFS networks; anything unrecognized falls into `otherDurationSeconds`.
`waitDurationSeconds` exists because `itinerary.duration` is door-to-door but
`legs[].duration` only covers time actually moving, not time spent waiting
at a stop between legs — it's computed as the remainder so the buckets
always sum to `durationSeconds` exactly.

Two ways this client deliberately differs from `nominatim.client.ts` /
`overpass.client.ts`:

- `OTP_URL` is read **lazily inside `planTrip`**, not at module load, and
  only throws when actually called without it set. It's not a secret and
  isn't in the import-time-fail-fast list under Config & secrets above — OTP
  is local infra that may not be running, and the rest of the app must still
  boot without it.
- No throttle. Nominatim/Overpass are shared public instances with usage
  policies; OTP here is self-hosted, so there's nothing to be polite to.

`routes` (`src/models/route.model.ts`) is **per-space**, unlike `pois` —
a travel time is only meaningful from one space's coordinates, so there's no
shared/deduped table here. `routing.service.ts`'s `computeRoutesForSpace` also
stores OTP's encoded geometry per itinerary leg, allowing the selected
POI-to-space route to be drawn with mode-specific styling. It deletes and
replaces all of a space's `routes` rows on every call, same delete-and-replace
convention `space_pois` uses (and the same non-atomicity caveat: the neon-http
driver has no transactions, so an interrupted request between delete and
insert can leave a space with zero routes until the next compute).

`computeRoutes` (`POST /api/spaces/:id/route`) 422s if the space has no
`lat`/`lng` (geocode first) or has no linked POIs (discover first — routing
to nothing isn't meaningful). `listRoutes` (`GET /api/spaces/:id/routes`)
joins `routes` → `pois` and reads back cached results without recomputing.

Standing up OTP itself (Docker + Madrid GTFS/OSM data) is a separate,
not-yet-completed step — see `docker/otp/README.md`. Hitting either endpoint
before that's done will fail when `planTrip` can't reach `OTP_URL`.

## University commutes

Each user can save one university destination in `universities`. The protected
`/api/university` routes geocode that address and calculate apartment-to-university
commutes through the same OTP client used by POI routing. Results are cached in
`university_commutes`, one row per university/space pair, and replaced only after
a complete recomputation. Each commute also stores OTP's encoded geometry per
itinerary leg so the frontend can draw the selected route with mode-specific
styling. Editing the university clears the now-stale commute rows.

The commute API derives the owner from `req.user.id`, only selects that user's
geocoded spaces, and never accepts a space or owner ID from the client.

---

## Adding a new feature

Two shapes come up: a feature that belongs to a signed-in user (data scoped
by `req.user.id`), and a feature that's public and doesn't touch the auth
flow at all (marketing/landing content, public config, health checks). Both
still go through the global pipeline (helmet, cors, identifyUser, Arcjet) —
"unscoped" only means "doesn't call `requireAuth`," not "skips the app-level
middleware."

### A. User-scoped feature (example: a "spaces" page)

1. **Model** — `src/models/space.model.ts`:

   ```ts
   import {
     pgTable,
     serial,
     integer,
     varchar,
     timestamp,
   } from 'drizzle-orm/pg-core';
   import { users } from '#models/user.model.ts';

   export const spaces = pgTable('spaces', {
     id: serial('id').primaryKey(),
     ownerId: integer('owner_id')
       .notNull()
       .references(() => users.id),
     name: varchar('name', { length: 255 }).notNull(),
     created_at: timestamp().defaultNow().notNull(),
   });
   ```

   Run `npm run db:generate` then `npm run db:migrate`.

2. **Validation** — `src/validations/space.validations.ts`, a Zod schema for
   the request body, same shape as `auth.validations.ts`. Remember: Zod's
   `z.object()` strips unknown keys by default, so don't worry about clients
   smuggling extra fields (e.g. `ownerId`) into the body — but also _never_
   trust a client-supplied owner/user id even so. Always derive ownership
   from `req.user.id`, not from the request body.

3. **Service** — `src/services/space.service.ts`. Plain functions
   (`createSpace`, `listSpacesForUser`, ...), Drizzle queries scoped by
   `ownerId`, no `req`/`res`.

4. **Controller** — `src/controllers/space.controller.ts`. Reads
   `req.user.id` (guaranteed present because the route below is guarded by
   `requireAuth`), validates the body via the Zod schema, calls the service,
   shapes the response. Follow `auth.controller.ts`'s pattern: `try/catch`,
   narrow caught errors with `e instanceof Error` before reading `.message`,
   `next(e)` on unexpected failures.

5. **Routes** — `src/routes/space.routes.ts`:

   ```ts
   import { Router } from 'express';
   import { requireAuth } from '#middleware/auth.middleware.ts';
   import { createSpace, listSpaces } from '#controllers/space.controller.ts';

   const router = Router();
   router.use(requireAuth); // every route below requires a session
   router.post('/', createSpace);
   router.get('/', listSpaces);
   export default router;
   ```

6. **Mount** in `src/app.ts`, after the global pipeline, alongside
   `authRoutes`:
   ```ts
   app.use('/api/spaces', spaceRoutes);
   ```

That's it — `identifyUser` already ran globally, `requireAuth` on the router
enforces the session, `req.user` is fully typed with no extra work.

### B. Public / unscoped feature (example: landing-page content API)

Same four layers, minus the auth requirement:

1. **Model / service** — same as above if it's backed by the DB (e.g. a
   `site_content` table), or skip the model entirely if it's static/config
   data with no persistence.
2. **Controller** — no `req.user` access. If you _want_ to optionally
   personalize a public response for logged-in users without requiring
   login, `req.user` is still available (`identifyUser` ran globally) —
   just guard it as optional: `if (req.user) { ... }`.
3. **Routes** — no `requireAuth`:
   ```ts
   const router = Router();
   router.get('/', getLandingContent);
   export default router;
   ```
4. **Mount** in `app.ts` the same way: `app.use('/api/landing', landingRoutes);`

The route is still rate-limited and shield-protected (global pipeline), just
at the `guest` tier since there's no session. Don't add a bespoke
"skip security middleware for this route" escape hatch — if a route
genuinely needs different treatment (e.g. a webhook endpoint that needs to
allow a specific bot), configure that as an Arcjet rule exception, not by
routing around `securityMiddleware`.

### Checklist for either kind

- [ ] New DB tables: migration generated and applied (`npm run db:generate` /
      `db:migrate`), never hand-edit an old migration.
- [ ] Request bodies validated with Zod before touching the service layer.
- [ ] Cross-layer imports use `#alias/*.ts`, not relative paths.
- [ ] Errors from `catch (e)` narrowed with `e instanceof Error` before
      reading `.message` (catch variables are `unknown`).
- [ ] Logging goes through `logger` (`#configs/logger.ts`), never
      `console.*`.
- [ ] New required secrets: fail fast at module load, no silent fallback.
- [ ] `npx tsc --noEmit` and `npx eslint .` both clean before considering the
      feature done.

---

## Known gaps

Not addressed yet, tracked here so they're not rediscovered from scratch:

- No global Express error-handling middleware — falls through to Express's
  default handler, which can leak stack traces if `NODE_ENV` isn't
  explicitly `production` at deploy time.
- Password policy is just `min(6)`, no complexity/breach check.

### Overpass failure handling

Discovery uses a 15-second Overpass query timeout and a 20-second HTTP timeout per attempt. Transient network errors, HTTP 429, and HTTP 5xx are retried up to three total attempts, alternating overpass-api.de and overpass.kumi.systems with 1s then 2s backoff. Other HTTP errors are not retried. Exhausted ECONNABORTED/ETIMEDOUT failures return HTTP 504 with `{ error: "POI discovery timed out", message: "The nearby places service timed out. Please try again shortly.", retryable: true }`. Existing POI links are untouched when Overpass fails. The discovery controller logs failures once, with only code, message, and spaceId metadata.
