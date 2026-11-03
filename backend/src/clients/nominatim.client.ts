import logger from '#configs/logger.ts';
import axios from 'axios';

const NOMINATIM_URL = 'https://nominatim.openstreetmap.org/search';

// Nominatim's (and Overpass's) usage policy requires a real identifying
// User-Agent and caps unauthenticated use at 1 request/sec. OSM_USER_AGENT
// is shared with overpass.client.ts since both are OSM infra with the same
// policy.
const USER_AGENT =
  process.env.OSM_USER_AGENT ??
  'Scout/1.0 (student housing app; set OSM_USER_AGENT with contact info)';

if (!process.env.OSM_USER_AGENT) {
  logger.warn(
    'OSM_USER_AGENT is not set — using a generic fallback. Nominatim may rate-limit or block requests without real contact info.'
  );
}

// This client is the only place Nominatim is called from, so a module-level
// timestamp is enough to serialize requests app-wide to the required 1/sec.
const MIN_INTERVAL_MS = 1000;
let lastRequestAt = 0;

const throttle = (): Promise<void> => {
  const elapsed = Date.now() - lastRequestAt;
  const wait = elapsed < MIN_INTERVAL_MS ? MIN_INTERVAL_MS - elapsed : 0;

  return new Promise(resolve => setTimeout(resolve, wait)).then(() => {
    lastRequestAt = Date.now();
  });
};

export interface GeocodeResult {
  lat: number;
  lng: number;
}

interface NominatimSearchResult {
  lat: string;
  lon: string;
}

export const geocodeAddress = (
  address: string
): Promise<GeocodeResult | null> => {
  return throttle()
    .then(() =>
      axios.get<NominatimSearchResult[]>(NOMINATIM_URL, {
        params: { q: address, format: 'json', limit: 1 },
        headers: { 'User-Agent': USER_AGENT },
      })
    )
    .then(res => {
      if (res.data.length === 0) return null;

      const [result] = res.data;
      return { lat: parseFloat(result.lat), lng: parseFloat(result.lon) };
    })
    .catch(e => {
      logger.error('Error geocoding address via Nominatim', e);
      throw e;
    });
};
