# Page-by-Page Content Spec

Covers every page in the app: the two auth pages, the Spaces list page, and a pointer to the
Space detail page (fully specified in
[`03-map-and-navigation.md`](./03-map-and-navigation.md)).

The `sampleui/` screenshots are shape references only — grouping structure, panel layout, card
anatomy — never visual style (colors, fonts, iconography are all shadcn defaults, not copied from
the samples).

## Derived status pipeline

The `spaces` table has **no status/stage column** — it's just
`id, ownerId, name, address, lat, lng, created_at, updated_at`. Rather than inventing a fake
persisted status (local-only or a new DB column), the Spaces table groups/labels spaces by a
**status computed client-side from real pipeline state**, in this order:

1. `not_geocoded` — `space.lat == null || space.lng == null`
2. `geocoded` — has coordinates, but its `pois` list is empty
3. `pois_discovered` — has POIs, but its `routes` list is empty
4. `routes_computed` — has both POIs and routes

This is purely informational — there's no drag-and-drop to "move" a space between statuses, since
status isn't a settable value; it just reflects what's actually been done to that space
(`geocode` → `discover` → `compute routes`, per the backend's own action endpoints).

**Known tradeoff, documented rather than solved now:** `GET /api/spaces` returns bare space rows
with no POI/route counts, so computing statuses 2–4 for every row on the list page means fetching
`pois` and `routes` per space (N+1 requests). This is acceptable at the expected scale (a user
with tens of spaces, not thousands) — cache aggressively with TanStack Query. If this ever becomes
a real cost, the fix is a backend enhancement (embed `poiCount`/`routeCount` in the list response),
not a frontend workaround.

## Auth pages

Both render inside `AuthLayout` — a centered card, per `sampleui/auth layout.png`'s shape.

### Sign Up (`/sign-up`)
Fields: `name`, `email`, `password`. (No phone number field — the sample image has one, but
there's no backend field for it, so it's dropped.) On submit → `POST /api/auth/sign-up`. On
success, navigate to `/spaces`. Link to "Already have an account? Sign in" → `/sign-in`.

### Sign In (`/sign-in`)
Fields: `email`, `password`. On submit → `POST /api/auth/sign-in`. On success, navigate to
`?from=` (if present, from a redirect out of `RequireAuth`) or `/spaces`. Link to
"Don't have an account? Sign up" → `/sign-up`.

## Spaces page (`/spaces`)

Shape reference: `sampleui/Spacelisting tableview.png` (grouped table).

**Header**: "Spaces" title + "New Space" button. Opens a dialog with a `name`/`address` form →
`POST /api/spaces`, then navigates straight to the new space's detail page
(`/spaces/:newSpaceId`) so the user can geocode/discover/compute routes immediately.

### Grouped table
Rows grouped into collapsible sections by derived status (mirrors the sample's grouped-by-status
layout — e.g. "Backlog" / "In progress" style sections, just with our own status labels).
Columns: **Name**, **Address**, **Status** (badge), **POI count**, **Created date**. Clicking a
row navigates to `/spaces/:id`.

### Empty state
No spaces yet → centered prompt ("Create your first space") that opens the same New Space dialog.

## Space detail page (`/spaces/:spaceId`)

Fully specified in [`03-map-and-navigation.md`](./03-map-and-navigation.md) — covers the Leaflet
map, the sidebar, POI/route data loading, and the navigation flow into this page from the Spaces
list.
