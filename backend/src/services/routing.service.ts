import logger from '#configs/logger.ts';
import { db } from '#configs/database.ts';
import { routes } from '#models/route.model.ts';
import { pois } from '#models/poi.model.ts';
import { planTripWithGeometry } from '#clients/otp.client.ts';
import { eq } from 'drizzle-orm';

// Firing planTrip for every POI at once (unbounded Promise.all) would burst
// this many simultaneous GraphQL requests at a resource-capped, 4-CPU local
// OTP instance (docker/otp/docker-compose.yml) — easily enough to overload
// it and make every request hang toward the 2-minute timeout instead of
// failing fast. Route requests one small batch at a time instead.
const OTP_CONCURRENCY = 3;

const planTripForPoi = async (
  spaceId: number,
  spaceLat: number,
  spaceLng: number,
  poi: { id: number; lat: number; lng: number; name?: string },
  progress: string
) => {
  logger.info(
    `[${progress}] Planning trip: POI ${poi.id}${poi.name ? ` (${poi.name})` : ''} -> space ${spaceId}`
  );

  const trip = await planTripWithGeometry(
    { lat: poi.lat, lng: poi.lng },
    { lat: spaceLat, lng: spaceLng }
  );

  if (!trip) {
    logger.warn(`No itinerary found from POI ${poi.id} to space ${spaceId}`);
    return null;
  }

  logger.info(
    `[${progress}] Done: POI ${poi.id} -> space ${spaceId}, ${trip.durationSeconds}s`
  );

  return { spaceId, poiId: poi.id, ...trip };
};

// Per-space delete-and-replace, same convention as discoverPoisForSpace in
// poi.service.ts, but simpler here — routes rows aren't shared across spaces
// the way pois rows are, so there's no upsert-into-a-shared-table step, just
// delete this space's old rows and insert the freshly computed ones.
//
// Not atomic — same neon-http caveat as poi.service.ts — an interrupted
// request between the delete and insert can leave a space with zero routes
// until the next compute call.
export const computeRoutesForSpace = async ({
  spaceId,
  spaceLat,
  spaceLng,
  pois: targets,
  onBatchProgress,
}: {
  spaceId: number;
  spaceLat: number;
  spaceLng: number;
  pois: { id: number; lat: number; lng: number; name?: string }[];
  onBatchProgress?: (completed: number, total: number) => void | Promise<void>;
}) => {
  try {
    logger.info(
      `Computing routes for space ${spaceId}: ${targets.length} POIs, ${OTP_CONCURRENCY} at a time`
    );

    const planned = [];

    for (let i = 0; i < targets.length; i += OTP_CONCURRENCY) {
      const batch = targets.slice(i, i + OTP_CONCURRENCY);
      const batchResults = await Promise.all(
        batch.map((poi, j) =>
          planTripForPoi(
            spaceId,
            spaceLat,
            spaceLng,
            poi,
            `${i + j + 1}/${targets.length}`
          )
        )
      );
      planned.push(...batchResults);
      await onBatchProgress?.(planned.length, targets.length);
    }

    const computed = planned.filter(
      (route): route is NonNullable<typeof route> => route !== null
    );

    await db.delete(routes).where(eq(routes.spaceId, spaceId));

    if (computed.length === 0) {
      logger.info(`No routes computed for space ${spaceId}`);
      return [];
    }

    const inserted = await db.insert(routes).values(computed).returning();

    logger.info(`Computed ${inserted.length} routes for space ${spaceId}`);
    return inserted;
  } catch (e) {
    logger.error('Error computing routes', e);
    throw e;
  }
};

export const listRoutesForSpace = async (spaceId: number) => {
  try {
    return await db
      .select({
        id: routes.id,
        poiId: routes.poiId,
        poiName: pois.name,
        poiCategory: pois.category,
        durationSeconds: routes.durationSeconds,
        walkDurationSeconds: routes.walkDurationSeconds,
        metroDurationSeconds: routes.metroDurationSeconds,
        busDurationSeconds: routes.busDurationSeconds,
        tramDurationSeconds: routes.tramDurationSeconds,
        otherDurationSeconds: routes.otherDurationSeconds,
        waitDurationSeconds: routes.waitDurationSeconds,
        routeGeometry: routes.routeGeometry,
        computed_at: routes.computed_at,
      })
      .from(routes)
      .innerJoin(pois, eq(routes.poiId, pois.id))
      .where(eq(routes.spaceId, spaceId));
  } catch (e) {
    logger.error('Error listing routes', e);
    throw e;
  }
};
