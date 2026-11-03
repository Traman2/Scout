import axios from 'axios';

const OVERPASS_URLS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
];
const QUERY_TIMEOUT_SECONDS = 15;
const HTTP_TIMEOUT_MS = (QUERY_TIMEOUT_SECONDS + 5) * 1000;
const MAX_ATTEMPTS = 3;

const isRetryable = (error: unknown): boolean =>
  axios.isAxiosError(error) &&
  ([
    'ECONNABORTED',
    'ETIMEDOUT',
    'ECONNRESET',
    'EAI_AGAIN',
    'ENOTFOUND',
    'ERR_NETWORK',
  ].includes(error.code ?? '') ||
    error.response?.status === 429 ||
    (error.response?.status ?? 0) >= 500);

const queryOverpass = async (query: string) => {
  for (let attempt = 0; ; attempt++) {
    await throttle();
    try {
      return await axios.post<OverpassResponse>(
        OVERPASS_URLS[attempt % OVERPASS_URLS.length],
        new URLSearchParams({ data: query }),
        { headers: { 'User-Agent': USER_AGENT }, timeout: HTTP_TIMEOUT_MS }
      );
    } catch (error) {
      if (attempt + 1 >= MAX_ATTEMPTS || !isRetryable(error)) throw error;
      await new Promise(resolve => setTimeout(resolve, 1000 * 2 ** attempt));
    }
  }
};

// Shared with nominatim.client.ts — both are OSM infra with the same
// identifying-User-Agent policy.
const USER_AGENT =
  process.env.OSM_USER_AGENT ??
  'Scout/1.0 (student housing app; set OSM_USER_AGENT with contact info)';

// v1: a fixed, hardcoded category → OSM tag mapping. Not user-configurable
// yet — that's a v2 concern (custom categories/weights).
const CATEGORY_FILTERS = [
  { category: 'grocery', key: 'shop', value: 'supermarket' },
  { category: 'gym', key: 'leisure', value: 'fitness_centre' },
  { category: 'transit', key: 'highway', value: 'bus_stop' },
  { category: 'transit', key: 'railway', value: 'station' },
] as const;

const categorize = (
  tags: Record<string, string> | undefined
): string | null => {
  if (!tags) return null;
  const match = CATEGORY_FILTERS.find(f => tags[f.key] === f.value);
  return match?.category ?? null;
};

const buildQuery = (lat: number, lng: number, radiusMeters: number) => {
  const clauses = CATEGORY_FILTERS.map(
    f => `node["${f.key}"="${f.value}"](around:${radiusMeters},${lat},${lng});`
  ).join('\n  ');

  return `[out:json][timeout:${QUERY_TIMEOUT_SECONDS}];\n(\n  ${clauses}\n);\nout center;`;
};

// Overpass's public instance asks for no more than a couple of concurrent
// requests and discourages tight polling. This client is the only place
// Overpass is called from, so a module-level timestamp is enough to
// serialize requests app-wide, same pattern as nominatim.client.ts.
const MIN_INTERVAL_MS = 1000;
let lastRequestAt = 0;

const throttle = (): Promise<void> => {
  const elapsed = Date.now() - lastRequestAt;
  const wait = elapsed < MIN_INTERVAL_MS ? MIN_INTERVAL_MS - elapsed : 0;

  return new Promise(resolve => setTimeout(resolve, wait)).then(() => {
    lastRequestAt = Date.now();
  });
};

export interface OverpassPoi {
  osmId: number;
  category: string;
  name: string;
  lat: number;
  lng: number;
}

interface OverpassElement {
  id: number;
  lat?: number;
  lon?: number;
  center?: { lat: number; lon: number };
  tags?: Record<string, string>;
}

interface OverpassResponse {
  elements: OverpassElement[];
}

export const findNearbyPois = (
  lat: number,
  lng: number,
  radiusMeters: number
): Promise<OverpassPoi[]> => {
  return queryOverpass(buildQuery(lat, lng, radiusMeters)).then(res =>
    res.data.elements.reduce<OverpassPoi[]>((pois, el) => {
      const name = el.tags?.name;
      const category = categorize(el.tags);
      const position = el.center ?? { lat: el.lat, lon: el.lon };

      if (name && category && position.lat != null && position.lon != null) {
        pois.push({
          osmId: el.id,
          category,
          name,
          lat: position.lat,
          lng: position.lon,
        });
      }

      return pois;
    }, [])
  );
};
