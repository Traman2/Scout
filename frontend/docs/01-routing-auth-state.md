# Routing, Auth, Cookies & State Management

This doc locks down the frontend architecture for routing, session/auth handling, and state
management before any pages are built. It reflects the **actual current backend behavior** — not
aspirational behavior — so it should be treated as the source of truth for how the frontend talks
to the API.

## Current frontend state (as of this writing)

- `react-router-dom` v7 and `@tanstack/react-query` v5 are installed but **not wired up** yet
  (`App.tsx` is the default Vite scaffold, `main.tsx` has no router/query provider).
- No `leaflet`/`react-leaflet`, no state library (no redux/zustand/jotai — intentionally, see
  below), no API client code exists yet.
- `react-hook-form`, `@hookform/resolvers`, and `zod` are installed for forms.
- shadcn/ui is available via the MCP server scoped to `frontend/` (`.mcp.json`), and the
  prerequisites (Tailwind v4 + `@tailwindcss/vite` plugin, `@/*` path alias in `vite.config.ts`
  and `tsconfig.app.json`) are already in place. Components are added on demand through the MCP —
  there's no separate "init" step to do first.

## Routing

Use React Router v7's **data router** (`createBrowserRouter`), not the declarative
`<BrowserRouter>`, so route `loader`s can prefetch data via TanStack Query later without a
rearchitecture.

Route tree:

```
/sign-in              public   AuthLayout
/sign-up              public   AuthLayout
/                      → redirect to /spaces
/spaces                protected   Spaces list (grouped table)
/spaces/:spaceId       protected   Space detail (map)
*                      404
```

- Public auth routes render inside an `AuthLayout` — a centered card, matching the shape of
  `sampleui/auth layout.png` (shape only, not its visual style).
- Protected routes are wrapped by a `RequireAuth` element:
  - Reads auth state (see below).
  - While the auth check is in flight, renders a loading skeleton.
  - If unauthenticated, redirects to `/sign-in?from=<original path>` so sign-in can redirect back.

## Auth & cookie contract (exact backend behavior)

All auth endpoints are mounted at `/api/auth`:

| Method | Path | Auth | Request body | Response |
|---|---|---|---|---|
| POST | `/api/auth/sign-up` | none | `{name, email, password}` | `201 {message, user:{id,name,email,role}}` |
| POST | `/api/auth/sign-in` | none | `{email, password}` | `200 {message, user:{id,name,email,role}}` |
| POST | `/api/auth/sign-out` | none | — | `200 {message}` |
| GET | `/api/auth/me` | cookie | — | `200 {user:{id,name,email,role}}` |

`/me` does a DB lookup by the JWT's `id` claim (`getUserById` in `auth.service.ts`) rather than
just echoing the decoded JWT payload, so its response shape matches `sign-up`/`sign-in` exactly —
`name` is always present, including after a hard refresh or a background refetch. (Earlier in
this project `/me` returned the raw JWT payload, which never carried `name`, causing the UI to
flip between showing the name and the email depending on whether the query had just been seeded
by a mutation or freshly fetched from `/me` — fixed by the DB lookup above.)

**Session cookie**: name `token`, `httpOnly`, `sameSite: 'strict'`, `secure` in production,
`maxAge` 24h — matching the JWT's own `1d` expiry (these two were previously mismatched at 15min
vs 1d, causing users to appear logged out client-side while the token was still valid
server-side; fixed in commit `166affa`. If the backend's cookie/JWT expiry values ever change,
keep them in sync).

**The cookie is httpOnly** — the frontend cannot read the token from JS at all. Auth state must
always be derived by asking the server (`GET /api/auth/me`), never by inspecting a cookie or
decoding a JWT client-side. There is no `Authorization` header support anywhere in the backend —
this is a pure cookie-session model.

**Every fetch must send `credentials: 'include'`.** The backend's CORS config requires an exact
origin match from the `CORS_ORIGIN` env var plus `credentials: true` — no wildcard origins.
Because the cookie is `sameSite: 'strict'`, the frontend and backend must be **same-site** (same
registrable domain — e.g. different ports on `localhost` in dev, or subdomains of the same domain
in prod) or the browser will never send the cookie on cross-site requests, `credentials: 'include'`
notwithstanding.

The frontend needs an env var (e.g. `VITE_API_URL`) pointing at the backend's base URL.

## API client

A single small module, `src/lib/api-client.ts`:
- Thin wrapper around `fetch`.
- Always sets `credentials: 'include'` and `Content-Type: application/json` for JSON bodies.
- Parses the backend's error shapes (`{error, message}` or `{error, details}` on validation
  failures) and throws a typed `ApiError { status, error, message }` so callers can branch on
  status (401 → treat as signed out, 404 → not found, 422 → domain-specific "missing coords / no
  POIs" states, etc.).

## Server state — TanStack Query

TanStack Query is the single source of truth for anything that comes from the backend. Query key
scheme:

```
['auth', 'me']                          — current user
['spaces']                              — space list
['spaces', spaceId]                     — one space
['spaces', spaceId, 'pois']             — POIs discovered for a space
['spaces', spaceId, 'routes']           — computed routes for a space
```

Mutations invalidate the relevant keys:
- sign-in / sign-up → invalidate `['auth','me']`
- sign-out → invalidate/clear `['auth','me']` (and consider clearing the whole query cache)
- create space → invalidate `['spaces']`
- geocode / update space → invalidate `['spaces', spaceId]` and `['spaces']`
- discover POIs → invalidate `['spaces', spaceId, 'pois']`
- compute routes → invalidate `['spaces', spaceId, 'routes']`
- delete space → invalidate `['spaces']`, remove `['spaces', spaceId, ...]` entries

## Client/UI state

No global state library is needed or should be added (none is currently installed — don't reach
for redux/zustand/jotai). Instead:
- **Shareable/bookmarkable UI state** lives in route paths or URL search params when a feature
  requires it.
- **Ephemeral UI state** (open dialogs, selected map marker, form state) is local component state.
- The current **user object** is read straight from the `['auth','me']` query cache via
  `useQuery` wherever it's needed — no need to duplicate it into React Context.

## Forms

`react-hook-form` + `zod` resolvers for every form, with zod schemas mirroring the backend's own
validation ranges so client-side errors match server-side ones:
- Sign-up: `name` (2–255 chars), `email` (valid email), `password` (6–128 chars).
- Sign-in: `email` (valid email), `password` (min 1 — backend doesn't re-validate length on
  sign-in, just presence).
- New/edit space: `name` (1–255 chars), `address` (1–1000 chars).

## shadcn components needed

Add via the MCP as pages are built (no separate setup step — the MCP is already scoped to
`frontend/` and the Tailwind/path-alias prerequisites are already in place):
`button`, `input`, `label`, `form`, `card`, `table`, `dialog`, `dropdown-menu`, `badge`,
`avatar`, `skeleton`, `sonner` (toasts), `separator`.
