---
name: connect-backend
description: "Use when building or extending the frontend's connection to the backend API — the fetch/API client, auth session handling (sign-up/sign-in/sign-out/me), CORS setup, or wiring a new backend endpoint into the frontend. For deciding *whether* a new feature needs backend changes and how to build them, use add-feature instead; this skill is specifically the frontend-side API client layer."
---

# Connecting the frontend to the backend

The frontend (`frontend/`, React + Vite) and backend (`backend/`, Express)
are separate origins in every environment — there's no proxy, no shared
process. Everything here follows from that plus one fact: **auth is a
signed, `httpOnly` cookie, not a token the frontend ever holds.** The
frontend can't read it, store it, or attach it manually — it can only ask
the browser to include it, and ask the backend who it belongs to.

> `frontend/` doesn't exist yet as of this writing. Everything below is the
> contract to build against and a recommended client shape, not a
> description of existing code — adjust file locations to whatever's
> actually scaffolded, but keep the request/auth behavior as specified;
> that part isn't optional, it's what the backend requires.

## The three things every request must get right

1. **`credentials: 'include'`** on every request (fetch) or
   `withCredentials: true` (axios). Without this, the browser never sends
   the `token` cookie, and every authenticated call silently 401s.
2. **The frontend's origin must be in the backend's `CORS_ORIGIN`** env var
   (`backend/.env`, comma-separated). If it's not, the browser blocks the
   request before it even reaches your code — you'll see a CORS error in
   devtools, not a 401. Dev default is `http://localhost:5173`; add every
   new deploy preview/staging/prod origin here as it comes into existence.
3. **Read the backend's base URL from an env var**, never hardcode it —
   Vite exposes env vars to client code only if prefixed `VITE_`:
   ```
   # frontend/.env
   VITE_API_URL=http://localhost:3000
   ```

## The API client

One module, everything else calls through it. Recommended shape
(`src/lib/api.ts` or wherever you keep shared client code):

```ts
const API_URL = import.meta.env.VITE_API_URL;

class ApiError extends Error {
  constructor(
    public status: number,
    public body: { error: string; message?: string; details?: string }
  ) {
    super(body.message ?? body.error);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...init.headers },
    ...init,
  });

  const body = await res.json().catch(() => ({}));

  if (!res.ok) {
    throw new ApiError(res.status, body);
  }

  return body as T;
}

export { request, ApiError };
```

Every backend error response is shaped `{ error: string, message?: string,
details?: string }` (see `backend/src/controllers/*.controller.ts` for the
exact strings per endpoint) — `ApiError` above preserves that instead of
collapsing it into a generic message, so call sites can branch on
`err.status` or `err.body.error` when they need to (e.g. show the specific
validation `details` under a form field).

**401 is not a generic error.** Anywhere a call can 401 (i.e. any
authenticated route — and opportunistically-authenticated ones too), that
almost always means "no session / session expired," not "something broke."
Handle it once, centrally — e.g. in `request()` above, or in whatever
session hook you build — by clearing local auth state and redirecting to
sign-in, rather than letting every call site show an error toast for it.

## Auth endpoints

These exist today in `backend/src/routes/auth.routes.ts`, mounted at
`/api/auth`:

| Endpoint | Method | Auth required | Body | Success shape |
|---|---|---|---|---|
| `/api/auth/sign-up` | POST | no | `{ name, email, password }` | `201 { message, user: {id,name,email,role} }` |
| `/api/auth/sign-in` | POST | no | `{ email, password }` | `200 { message, user: {id,name,email,role} }` |
| `/api/auth/sign-out` | POST | no | — | `200 { message }` |
| `/api/auth/me` | GET | **yes** | — | `200 { user: AuthTokenPayload }` |

`sign-up`/`sign-in` are what set the cookie (via `Set-Cookie` on the
response — you don't do anything with the response body to "store" the
session, the browser already has it). `sign-out` clears it. `me` is how the
frontend finds out who's logged in, since it can't read the cookie itself.

Mirror the user shape as a TypeScript type on the frontend (there's no
shared types package between the two apps yet, so this is kept in sync by
hand — if it drifts, `/me`'s actual response is the source of truth, not
this file):

```ts
interface User {
  id: number;
  email: string;
  role: string;
}
```

## Session state pattern

Because the session lives in a cookie the frontend can't inspect, "is the
user logged in" is server-derived, not something you can compute
client-side at app boot. The standard pattern: call `GET /api/auth/me` once
on app load (and after sign-in/sign-out) to establish auth state, treating
its 401 as "logged out," not an error:

```ts
async function getCurrentUser(): Promise<User | null> {
  try {
    const { user } = await request<{ user: User }>('/api/auth/me');
    return user;
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  }
}
```

Wrap that in whatever state layer you're using (context, a query library's
cache, etc.) — the specific mechanism isn't prescribed here, but the shape
("fetch `/me`, 401 means logged-out, anything else is a real error") is.

## Wiring up a new backend endpoint

When `add-feature` adds a new backend route, add the corresponding call to
the API client rather than calling `fetch` ad hoc from a component:

```ts
export function createSpace(data: { name: string }) {
  return request<{ id: number; name: string }>('/api/spaces', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}
```

Same rules apply regardless of whether the backend route is user-scoped or
public — `credentials: 'include'` is harmless on a public route (the
backend's `identifyUser` middleware just ignores the cookie if the route
doesn't require it).

## Common failure modes and what they actually mean

- **CORS error in devtools, request never shows in Network as completed** →
  frontend origin isn't in the backend's `CORS_ORIGIN`.
- **401 on every request, cookie visibly set in devtools Application tab**
  → `credentials: 'include'` missing on the fetch call.
- **401 immediately after a successful sign-in** → check `sameSite`/`secure`
  cookie attributes aren't being rejected by the browser (e.g. testing over
  plain `http://` in an environment where `secure` got set — `secure` is
  only forced in `NODE_ENV=production` on the backend, so this shouldn't
  happen in dev, but double-check if you've changed that).
- **Trying to read the JWT / decode it client-side** → don't; it's
  `httpOnly` by design so frontend JS can't access it (mitigates XSS token
  theft). If you find yourself wanting to read a claim from it client-side,
  that's a sign it should come from `/me`'s response instead.