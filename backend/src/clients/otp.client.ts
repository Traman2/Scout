import logger from '#configs/logger.ts';
import axios from 'axios';

// Unlike nominatim.client.ts / overpass.client.ts, OTP_URL is read lazily
// inside planTrip rather than failing fast at module load. Those two hit
// fixed public OSM endpoints; OTP is self-hosted local infra that may not be
// running yet, and the rest of the app (spaces/POIs) must still boot without
// it. Also unlike those two clients, there's no throttle here — OTP is
// self-hosted, not a shared public instance with a usage policy to respect.
const getOtpUrl = (): string => {
  const url = process.env.OTP_URL;
  if (!url) {
    throw new Error(
      'OTP_URL is not set — start the OTP instance (see docker/otp/README.md) and set OTP_URL to reach it'
    );
  }
  return url;
};

export interface TripDuration {
  durationSeconds: number;
  walkDurationSeconds: number;
  metroDurationSeconds: number;
  busDurationSeconds: number;
  tramDurationSeconds: number;
  otherDurationSeconds: number;
  waitDurationSeconds: number;
}

export interface RouteGeometryLeg {
  mode: string;
  encodedPolyline: string;
}

export interface TripPlan extends TripDuration {
  routeGeometry: RouteGeometryLeg[];
}

// v1: a fixed mapping from OTP's leg.mode to one of Madrid's three loaded
// GTFS networks (Metro, EMT bus, Metro Ligero tram) — same "fixed, not
// user-configurable yet" approach as CATEGORY_FILTERS in overpass.client.ts.
// Anything else (e.g. a future Cercanías/RAIL feed) falls into
// otherDurationSeconds rather than being silently dropped, so
// durationSeconds always equals the sum of every bucket (including
// waitDurationSeconds — see below).
const MODE_BUCKETS: Record<
  string,
  keyof Omit<TripDuration, 'durationSeconds' | 'waitDurationSeconds'>
> = {
  WALK: 'walkDurationSeconds',
  SUBWAY: 'metroDurationSeconds',
  BUS: 'busDurationSeconds',
  TRAM: 'tramDurationSeconds',
};

interface PlanLeg {
  mode: string;
  duration: number;
  legGeometry?: {
    points?: string;
  };
}

interface PlanQueryResponse {
  data?: {
    plan?: {
      itineraries: { duration: number; legs: PlanLeg[] }[];
    };
  };
  errors?: { message: string }[];
}

const PLAN_QUERY = `
  query Plan(
    $fromLat: Float!
    $fromLon: Float!
    $toLat: Float!
    $toLon: Float!
    $includeGeometry: Boolean!
  ) {
    plan(
      from: { lat: $fromLat, lon: $fromLon }
      to: { lat: $toLat, lon: $toLon }
      transportModes: [{ mode: WALK }, { mode: TRANSIT }]
      numItineraries: 1
    ) {
      itineraries {
        duration
        legs {
          mode
          duration
          legGeometry @include(if: $includeGeometry) {
            points
          }
        }
      }
    }
  }
`;

// GraphQL field names verified against OTP's public docs at plan time, not
// against a live instance (none was running in this session) — cross-check
// against the running instance's GraphiQL explorer (at {OTP_URL}/otp/gtfs/v1)
// once docker/otp is actually up, since these can shift between OTP versions.
const requestTrip = (
  from: { lat: number; lng: number },
  to: { lat: number; lng: number },
  includeGeometry: boolean
): Promise<TripPlan | null> => {
  return axios
    .post<PlanQueryResponse>(
      `${getOtpUrl()}/otp/gtfs/v1`,
      {
        query: PLAN_QUERY,
        variables: {
          fromLat: from.lat,
          fromLon: from.lng,
          toLat: to.lat,
          toLon: to.lng,
          includeGeometry,
        },
      },
      // 2 minutes, not the 20s the other clients use — this local OTP
      // instance is resource-capped (docker-compose.yml) and can be slow to
      // respond under memory/CPU pressure, unlike Nominatim/Overpass's
      // always-warm public instances.
      { headers: { 'Content-Type': 'application/json' }, timeout: 120000 }
    )
    .then(res => {
      if (res.data.errors?.length) {
        throw new Error(
          `OTP GraphQL error: ${res.data.errors.map(e => e.message).join('; ')}`
        );
      }

      const itinerary = res.data.data?.plan?.itineraries[0];
      if (!itinerary) return null;

      const buckets: Omit<
        TripDuration,
        'durationSeconds' | 'waitDurationSeconds'
      > = {
        walkDurationSeconds: 0,
        metroDurationSeconds: 0,
        busDurationSeconds: 0,
        tramDurationSeconds: 0,
        otherDurationSeconds: 0,
      };

      for (const leg of itinerary.legs) {
        const bucket = MODE_BUCKETS[leg.mode] ?? 'otherDurationSeconds';
        buckets[bucket] += leg.duration;
      }

      // itinerary.duration is door-to-door; legs[].duration only covers time
      // actually moving, not time spent waiting at a stop between legs (e.g.
      // walk to the metro, then wait for the next train). Without this, that
      // wait time would just vanish instead of showing up anywhere.
      const legsTotal = Object.values(buckets).reduce((a, b) => a + b, 0);
      const waitDurationSeconds = Math.max(0, itinerary.duration - legsTotal);

      return {
        durationSeconds: itinerary.duration,
        ...buckets,
        waitDurationSeconds,
        routeGeometry: itinerary.legs.flatMap(leg =>
          leg.legGeometry?.points
            ? [{ mode: leg.mode, encodedPolyline: leg.legGeometry.points }]
            : []
        ),
      };
    })
    .catch(e => {
      // Extract a plain message and rethrow a clean Error — never the raw
      // error. An AxiosError's .request carries the live Node
      // http.ClientRequest, which links into the process's whole active
      // Timeout chain (every open socket's keep-alive timer, not just this
      // one), so any catch up the call chain that logs the raw error again
      // (routing.service.ts, space.controller.ts) would reproduce the same
      // unreadable dump one layer up — sanitizing only here isn't enough if
      // the raw object keeps propagating.
      const message = axios.isAxiosError(e)
        ? `${e.code ?? e.message}${e.response ? ` (status ${e.response.status})` : ''}`
        : e instanceof Error
          ? e.message
          : String(e);
      logger.error(`Error planning trip via OTP: ${message}`);
      throw new Error(`OTP request failed: ${message}`);
    });
};

export const planTrip = async (
  from: { lat: number; lng: number },
  to: { lat: number; lng: number }
): Promise<TripDuration | null> => {
  const trip = await requestTrip(from, to, false);
  if (!trip) return null;

  return {
    durationSeconds: trip.durationSeconds,
    walkDurationSeconds: trip.walkDurationSeconds,
    metroDurationSeconds: trip.metroDurationSeconds,
    busDurationSeconds: trip.busDurationSeconds,
    tramDurationSeconds: trip.tramDurationSeconds,
    otherDurationSeconds: trip.otherDurationSeconds,
    waitDurationSeconds: trip.waitDurationSeconds,
  };
};

export const planTripWithGeometry = (
  from: { lat: number; lng: number },
  to: { lat: number; lng: number }
): Promise<TripPlan | null> => requestTrip(from, to, true);
