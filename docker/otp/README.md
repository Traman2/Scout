# OpenTripPlanner (Madrid)

Self-hosted transit routing for the `routing.service.ts` / `otp.client.ts`
backend feature. **Not set up or validated yet** — this is the setup
procedure to run once, not something already working.

## 1. Get the data

Download both files into `docker/otp/data/`. OTP requires GTFS filenames to
contain the word "gtfs", or the graph build silently ignores them.

- **OSM extract** — direct download, verified working:
  ```
  https://download.geofabrik.de/europe/spain/madrid-latest.osm.pbf
  ```
- **GTFS feeds** — CRTM's open data portal is a JS-rendered ArcGIS Hub page,
  so there's no stable direct file URL to link here. Visit each dataset page
  below and use its "Download" button, choosing the GTFS format:
  - Metro: https://datos.crtm.es/datasets/gtfs-red-de-metro
  - EMT (urban bus): https://datos.crtm.es/datasets/868df0e58fca47e79b942902dffd7da0
  - Metro Ligero (tram): https://datos.crtm.es/datasets/aaed26cc0ff64b0c947ac0bc3e033196

  Save each zip with "gtfs" in the name, e.g. `metro-gtfs.zip`,
  `emt-gtfs.zip`, `metro-ligero-gtfs.zip`. OTP will load all of them into one
  graph alongside the OSM extract.

Expected layout:

```
docker/otp/data/
  madrid-latest.osm.pbf
  metro-gtfs.zip
  emt-gtfs.zip
  metro-ligero-gtfs.zip
```

## 2. Build the graph (one-off, run once per data update)

```sh
docker compose -f docker/otp/docker-compose.yml run --rm otp --build --save
```

This loads everything in `data/` into memory and writes `graph.obj` back into
that same directory. It's memory-hungry — `JAVA_TOOL_OPTIONS=-Xmx4g` in
`docker-compose.yml` should be enough for Madrid's extract; raise it if the
build OOMs.

## 3. Serve it

```sh
docker compose -f docker/otp/docker-compose.yml up
```

Loads the saved `graph.obj` and serves the GTFS GraphQL API on port 8080.
Browse it at `http://localhost:8080/`, but set `OTP_URL` in `backend/.env` to
`http://127.0.0.1:8080` specifically, not `localhost` — Node resolves
"localhost" to `::1` first, and Docker Desktop's Windows port-forwarding
doesn't reliably accept that, so the backend's requests just hang instead of
connecting. Browsers don't hit this, so GraphiQL works fine with either.

Sanity-check with the GraphiQL explorer at `http://localhost:8080/` before
trusting `otp.client.ts`'s query shape — OTP2's GraphQL schema has shifted
between versions, and this hasn't been checked against a live instance yet.
