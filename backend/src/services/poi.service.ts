import logger from '#configs/logger.ts';
import { db } from '#configs/database.ts';
import { pois } from '#models/poi.model.ts';
import { spacePois } from '#models/space-poi.model.ts';
import { findNearbyPois } from '#clients/overpass.client.ts';
import { eq, sql } from 'drizzle-orm';

export const DEFAULT_POI_RADIUS_METERS = 1500;

// pois are shared across all spaces (deduped by OSM node id via the unique
// constraint on osmId) — discovering for one space upserts into that shared
// table rather than creating a duplicate row per space. Only this space's
// *links* in space_pois are deleted-and-replaced on re-discover, never the
// shared pois rows themselves (another space may still reference them).
//
// Not atomic — the neon-http driver has no transaction support (see
// Database in ARCHITECTURE.md) — so an interrupted request between the
// link-delete and link-insert can leave a space with zero linked POIs
// until the next discover. The shared pois table itself is never left in
// a partial state, since the upsert is a single statement.
export const discoverPoisForSpace = async ({
  spaceId,
  lat,
  lng,
  radiusMeters,
}: {
  spaceId: number;
  lat: number;
  lng: number;
  radiusMeters: number;
}) => {
  const found = await findNearbyPois(lat, lng, radiusMeters);

  await db.delete(spacePois).where(eq(spacePois.spaceId, spaceId));

  if (found.length === 0) {
    logger.info(`No POIs found for space ${spaceId}`);
    return [];
  }

  const upserted = await db
    .insert(pois)
    .values(
      found.map(poi => ({
        osmId: poi.osmId,
        category: poi.category,
        name: poi.name,
        lat: poi.lat,
        lng: poi.lng,
      }))
    )
    .onConflictDoUpdate({
      target: pois.osmId,
      set: {
        category: sql`excluded.category`,
        name: sql`excluded.name`,
        lat: sql`excluded.lat`,
        lng: sql`excluded.lng`,
      },
    })
    .returning();

  await db
    .insert(spacePois)
    .values(upserted.map(poi => ({ spaceId, poiId: poi.id })));

  logger.info(`Discovered ${upserted.length} POIs for space ${spaceId}`);
  return upserted;
};

export const listPoisForSpace = async (spaceId: number) => {
  try {
    return await db
      .select({
        id: pois.id,
        osmId: pois.osmId,
        category: pois.category,
        name: pois.name,
        lat: pois.lat,
        lng: pois.lng,
        created_at: pois.created_at,
      })
      .from(spacePois)
      .innerJoin(pois, eq(spacePois.poiId, pois.id))
      .where(eq(spacePois.spaceId, spaceId));
  } catch (e) {
    logger.error('Error listing POIs', e);
    throw e;
  }
};
