# Map & Navigation — Space Detail Page

Covers the `/spaces/:spaceId` page: how a user gets there from the Spaces list, how its data
loads, and how the Leaflet map renders the space, its discovered POIs, and computed routes.

Shape reference: `sampleui/map over layout.png` (sidebar list + map, with a bottom detail card
for the selected item) — shape only, not visual style.

## New dependency

`leaflet` + `react-leaflet` are **not currently installed** — add them as part of implementing
this page. Also required:
- `import 'leaflet/dist/leaflet.css'` in `main.tsx`.
- The standard Leaflet-in-a-bundler default-marker-icon fix (Leaflet's default icon URLs resolve
  relative to the page, not the bundle, under Vite — override `L.Icon.Default`'s `iconUrl` /
  `iconRetinaUrl` / `shadowUrl` with imported asset URLs).

## Navigation flow

From the Spaces list (either view), clicking a card/row calls `useNavigate()` (or renders a
`<Link>`) to `/spaces/${space.id}`. The detail page reads `spaceId` via `useParams<{spaceId:
string}>()`.

On mount (or via a route `loader` calling `queryClient.ensureQueryData`, once loaders are wired
up), fire three parallel queries:

```
['spaces', spaceId]              GET /api/spaces/:id
['spaces', spaceId, 'pois']      GET /api/spaces/:id/pois
['spaces', spaceId, 'routes']    GET /api/spaces/:id/routes
```

If the space request 404s (not found, or owned by a different user — the backend scopes every
space route by `ownerId` and returns 404 either way), render a not-found state with a link back
to `/spaces`.

## Layout

Left sidebar panel + right map filling the remaining space, per the sample's shape.

### Sidebar
- Space name, address, geocode status.
- Action buttons, shown based on pipeline state (mirrors the derived status from
  `02-pages.md`):
  - Ungeocoded → **Geocode** button → `POST /api/spaces/:id/geocode`.
  - Geocoded, no POIs → **Discover POIs** button → `POST /api/spaces/:id/discover` (default
    `radiusMeters` 1500; could later expose the 100–5000 range as an input).
  - POIs exist, no routes → **Compute Routes** button → `POST /api/spaces/:id/route`.
- Below the actions, a scrollable list of discovered POIs: name, category icon
  (`grocery`/`gym`/`transit`), and — once routes are computed — a compact travel-time summary per
  POI.

### Map
- `MapContainer` centered on `[space.lat, space.lng]` (only rendered once the space is geocoded —
  see empty states below).
- OSM `TileLayer` with proper attribution.
- A distinct marker for the space itself (different icon/color from POI markers).
- One marker per POI, styled by `category`.

### Cross-referencing POIs and routes
The `routes` endpoint returns `{id, poiId, poiName, poiCategory, durationSeconds,
walkDurationSeconds, metroDurationSeconds, busDurationSeconds, tramDurationSeconds,
otherDurationSeconds, waitDurationSeconds, routeGeometry, computed_at}` — **no lat/lng**. There is no combined
backend endpoint that returns space + POIs + routes together. To plot or highlight a route's POI
on the map, join `routes[i].poiId` against the `pois` list (which does have `lat`/`lng`)
client-side.

### Interaction
Clicking a POI marker on the map or its row in the sidebar list highlights the other (shared
`selectedPoiId` local state). If routes have been computed, this also opens a detail panel —
echoing the sample's bottom "Order #30427"-style card — showing that POI's full duration
breakdown: `durationSeconds` (total), `walkDurationSeconds`, `metroDurationSeconds`,
`busDurationSeconds`, `tramDurationSeconds`, `otherDurationSeconds`, `waitDurationSeconds`.
The selected POI-to-apartment route's OTP geometry is drawn on the map one leg at a time, with distinct styling
for walking, metro, bus, tram, and other transit modes. Selecting a route fits the map to its
full geometry; routes computed before geometry storage was added must be recomputed once.

### Empty states, in pipeline order
1. **Ungeocoded** (`lat`/`lng` null) — map area replaced by an empty state with a "Geocode this
   space" prompt/button (same action as the sidebar button).
2. **Geocoded, no POIs** — map renders with just the space marker; sidebar prompts
   "Discover nearby POIs".
3. **POIs, no routes** — map renders POI markers; sidebar prompts "Compute routes"; no
   travel-time detail panel yet since there's nothing to show.
4. **Routes computed** — full experience: markers + sidebar list + click-to-see travel-time panel.
