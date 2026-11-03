import logger from '#configs/logger.ts';
import { db } from '#configs/database.ts';
import { spaces } from '#models/space.model.ts';
import { geocodeAddress } from '#clients/nominatim.client.ts';
import { and, eq } from 'drizzle-orm';

// Geocoding failure never blocks a create/update — the space is still
// saved with null coordinates, and callers can retry via geocodeSpace.
export type GeocodeAttempt =
  | { status: 'succeeded'; coordinates: { lat: number; lng: number } }
  | { status: 'failed'; error: unknown };

const tryGeocode = async (address: string): Promise<GeocodeAttempt> => {
  try {
    const result = await geocodeAddress(address);
    if (!result) {
      logger.warn('Geocoding found no match for a space address');
      return { status: 'failed', error: new Error('Address not found') };
    }
    return { status: 'succeeded', coordinates: result };
  } catch (e) {
    logger.warn('Geocoding request failed, continuing without coordinates');
    return { status: 'failed', error: e };
  }
};

export const createSpace = async ({
  ownerId,
  name,
  address,
}: {
  ownerId: number;
  name: string;
  address: string;
}) => {
  try {
    const geocodeAttempt = await tryGeocode(address);
    const coordinates =
      geocodeAttempt.status === 'succeeded' ? geocodeAttempt.coordinates : null;

    const [newSpace] = await db
      .insert(spaces)
      .values({
        ownerId,
        name,
        address,
        lat: coordinates?.lat,
        lng: coordinates?.lng,
      })
      .returning();

    logger.info(`Space ${newSpace.id} created for user ${ownerId}`);
    return { space: newSpace, geocodeAttempt };
  } catch (e) {
    logger.error('Error creating space', e);
    throw e;
  }
};

export const listSpacesForUser = async (ownerId: number) => {
  try {
    return await db.select().from(spaces).where(eq(spaces.ownerId, ownerId));
  } catch (e) {
    logger.error('Error listing spaces', e);
    throw e;
  }
};

export const getSpaceForUser = async ({
  id,
  ownerId,
}: {
  id: number;
  ownerId: number;
}) => {
  try {
    const [space] = await db
      .select()
      .from(spaces)
      .where(and(eq(spaces.id, id), eq(spaces.ownerId, ownerId)))
      .limit(1);

    if (!space) throw new Error('Space not found');

    return space;
  } catch (e) {
    logger.error('Error fetching space', e);
    throw e;
  }
};

export const updateSpaceForUser = async ({
  id,
  ownerId,
  data,
}: {
  id: number;
  ownerId: number;
  data: Partial<{ name: string; address: string; lat: number; lng: number }>;
}) => {
  try {
    const { lat, lng, ...rest } = data;
    const geocodeAttempt = rest.address ? await tryGeocode(rest.address) : null;
    const coordinates =
      geocodeAttempt?.status === 'succeeded'
        ? geocodeAttempt.coordinates
        : null;

    const [updated] = await db
      .update(spaces)
      .set({
        ...rest,
        ...(coordinates && { lat: coordinates.lat, lng: coordinates.lng }),
        // Explicit lat/lng (e.g. from the manual-coordinates form) always
        // wins over an address-triggered geocode result.
        ...(lat !== undefined && { lat }),
        ...(lng !== undefined && { lng }),
        updated_at: new Date(),
      })
      .where(and(eq(spaces.id, id), eq(spaces.ownerId, ownerId)))
      .returning();

    if (!updated) throw new Error('Space not found');

    logger.info(`Space ${id} updated for user ${ownerId}`);
    return { space: updated, geocodeAttempt };
  } catch (e) {
    logger.error('Error updating space', e);
    throw e;
  }
};

export const geocodeSpace = async ({
  id,
  ownerId,
}: {
  id: number;
  ownerId: number;
}) => {
  try {
    const space = await getSpaceForUser({ id, ownerId });

    const geocoded = await geocodeAddress(space.address);

    if (!geocoded) throw new Error('Address not found');

    const [updated] = await db
      .update(spaces)
      .set({ lat: geocoded.lat, lng: geocoded.lng, updated_at: new Date() })
      .where(and(eq(spaces.id, id), eq(spaces.ownerId, ownerId)))
      .returning();

    logger.info(`Space ${id} geocoded for user ${ownerId}`);
    return updated;
  } catch (e) {
    logger.error('Error geocoding space', e);
    throw e;
  }
};

export const deleteSpaceForUser = async ({
  id,
  ownerId,
}: {
  id: number;
  ownerId: number;
}) => {
  try {
    const [deleted] = await db
      .delete(spaces)
      .where(and(eq(spaces.id, id), eq(spaces.ownerId, ownerId)))
      .returning({ id: spaces.id });

    if (!deleted) throw new Error('Space not found');

    logger.info(`Space ${id} deleted for user ${ownerId}`);
    return deleted;
  } catch (e) {
    logger.error('Error deleting space', e);
    throw e;
  }
};
