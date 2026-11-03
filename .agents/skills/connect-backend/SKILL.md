---
name: connect-backend
description: Build or extend this repository's frontend connection to its backend API, including the shared API client, cookie-based auth session handling, CORS configuration, and frontend calls to backend endpoints. Use for frontend-side API integration; use add-feature for broader full-stack feature work.
---

# Connect the frontend to the backend

The React + Vite frontend in `frontend/` and Express backend in `backend/` use separate origins. Authentication is held in a signed `httpOnly` cookie, not a token exposed to frontend JavaScript. The frontend must ask the browser to include the cookie and use the backend to resolve the current user.

## Request contract

Every request must:

1. Set `credentials: 'include'` with `fetch`, or `withCredentials: true` with Axios.
2. Use a frontend origin included in the backend's comma-separated `CORS_ORIGIN` setting. Development normally uses `http://localhost:5173`.
3. Read the backend URL from `VITE_API_URL`; never hardcode it.

```dotenv
VITE_API_URL=http://localhost:3000
```

Inspect the existing frontend before choosing file locations or state management. Centralize calls in its shared API module rather than calling `fetch` directly from components. If no client exists, use this shape as a starting point:

```ts
const API_URL = import.meta.env.VITE_API_URL;

class ApiError extends Error {
  constructor(
    public status: number,
    public body: { error: string; message?: string; details?: string },
  ) {
    super(body.message ?? body.error);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...init.headers },
    ...init,
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(response.status, body);
  return body as T;
}

export { request, ApiError };
```

Preserve the backend error shape `{ error: string, message?: string, details?: string }` so callers can branch on status or error code. Handle authenticated 401 responses centrally as a missing or expired session: clear local auth state and route to sign-in when appropriate instead of showing a generic error toast.

## Authentication endpoints

Confirm response types against the current backend controllers. The routes are mounted at `/api/auth`:

| Endpoint | Method | Auth | Body | Success |
|---|---|---|---|---|
| `/api/auth/sign-up` | POST | Public | `{ name, email, password }` | `201 { message, user }` |
| `/api/auth/sign-in` | POST | Public | `{ email, password }` | `200 { message, user }` |
| `/api/auth/sign-out` | POST | Public | None | `200 { message }` |
| `/api/auth/me` | GET | Required | None | `200 { user }` |

Sign-up and sign-in set the cookie through `Set-Cookie`; do not store a token from the response. Sign-out clears it. Resolve initial session state by calling `/api/auth/me`; interpret only its 401 response as logged out and propagate other failures.

```ts
async function getCurrentUser(): Promise<User | null> {
  try {
    const { user } = await request<{ user: User }>('/api/auth/me');
    return user;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}
```

Keep the frontend `User` type aligned with the actual `/me` response. The backend response is the source of truth.

## Add endpoint calls

Expose a typed function from the shared API layer:

```ts
export function createSpace(data: { name: string }) {
  return request<{ id: number; name: string }>('/api/spaces', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}
```

Use `credentials: 'include'` for both public and authenticated endpoints.

## Diagnose common failures

- A browser CORS error before a completed response usually means the frontend origin is absent from `CORS_ORIGIN`.
- Repeated 401s while a cookie is present usually indicate missing request credentials.
- A 401 immediately after successful sign-in can indicate incompatible `sameSite` or `secure` cookie attributes for the environment.
- Never read or decode the JWT in frontend code. Add needed session claims to `/me` instead.
