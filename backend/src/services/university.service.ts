import { geocodeAddress } from '#clients/nominatim.client.ts';
import { planTripWithGeometry } from '#clients/otp.client.ts';
import { db } from '#configs/database.ts';
import logger from '#configs/logger.ts';
import { spaces } from '#models/space.model.ts';
import { universities, universityCommutes } from '#models/university.model.ts';
import { and, eq, isNotNull } from 'drizzle-orm';

const OTP_CONCURRENCY = 3;

export const getUniversityForUser = async (ownerId: number) => {
  try {
    const [university] = await db
      .select()
      .from(universities)
      .where(eq(universities.ownerId, ownerId))
      .limit(1);

    return university ?? null;
  } catch (e) {
    logger.error('Error fetching university', e);
    throw e;
  }
};

export const saveUniversityForUser = async ({
  ownerId,
  name,
  address,
}: {
  ownerId: number;
  name: string;
  address: string;
}) => {
  try {
    const geocoded = await geocodeAddress(address);
    if (!geocoded) throw new Error('University address not found');

    const [university] = await db
      .insert(universities)
      .values({
        ownerId,
        name,
        address,
        lat: geocoded.lat,
        lng: geocoded.lng,
      })
      .onConflictDoUpdate({
        target: universities.ownerId,
        set: {
          name,
          address,
          lat: geocoded.lat,
          lng: geocoded.lng,
          updated_at: new Date(),
        },
      })
      .returning();

    // Existing commutes refer to the previous destination coordinates.
    await db
      .delete(universityCommutes)
      .where(eq(universityCommutes.universityId, university.id));

    logger.info(`University saved for user ${ownerId}`);
    return university;
  } catch (e) {
    logger.error('Error saving university', e);
    throw e;
  }
};

export const listUniversityCommutesForUser = async (ownerId: number) => {
  try {
    const university = await getUniversityForUser(ownerId);
    if (!university) return [];

    return await db
      .select({
        id: universityCommutes.id,
        spaceId: universityCommutes.spaceId,
        spaceName: spaces.name,
        spaceAddress: spaces.address,
        spaceLat: spaces.lat,
        spaceLng: spaces.lng,
        durationSeconds: universityCommutes.durationSeconds,
        walkDurationSeconds: universityCommutes.walkDurationSeconds,
        metroDurationSeconds: universityCommutes.metroDurationSeconds,
        busDurationSeconds: universityCommutes.busDurationSeconds,
        tramDurationSeconds: universityCommutes.tramDurationSeconds,
        otherDurationSeconds: universityCommutes.otherDurationSeconds,
        waitDurationSeconds: universityCommutes.waitDurationSeconds,
        routeGeometry: universityCommutes.routeGeometry,
        computed_at: universityCommutes.computed_at,
      })
      .from(universityCommutes)
      .innerJoin(spaces, eq(universityCommutes.spaceId, spaces.id))
      .where(
        and(
          eq(universityCommutes.universityId, university.id),
          eq(spaces.ownerId, ownerId)
        )
      );
  } catch (e) {
    logger.error('Error listing university commutes', e);
    throw e;
  }
};

export const computeUniversityCommutesForUser = async (ownerId: number) => {
  try {
    const university = await getUniversityForUser(ownerId);
    if (!university) throw new Error('University not configured');

    const geocodedSpaces = await db
      .select()
      .from(spaces)
      .where(
        and(
          eq(spaces.ownerId, ownerId),
          isNotNull(spaces.lat),
          isNotNull(spaces.lng)
        )
      );

    if (geocodedSpaces.length === 0) {
      throw new Error('No geocoded spaces');
    }

    const computed: (typeof universityCommutes.$inferInsert)[] = [];

    for (let i = 0; i < geocodedSpaces.length; i += OTP_CONCURRENCY) {
      const batch = geocodedSpaces.slice(i, i + OTP_CONCURRENCY);
      const results = await Promise.all(
        batch.map(async space => {
          if (space.lat === null || space.lng === null) return null;

          const trip = await planTripWithGeometry(
            { lat: space.lat, lng: space.lng },
            { lat: university.lat, lng: university.lng }
          );

          return trip
            ? {
                universityId: university.id,
                spaceId: space.id,
                ...trip,
              }
            : null;
        })
      );

      computed.push(
        ...results.filter(
          (commute): commute is NonNullable<typeof commute> => commute !== null
        )
      );
    }

    await db
      .delete(universityCommutes)
      .where(eq(universityCommutes.universityId, university.id));

    if (computed.length > 0) {
      await db.insert(universityCommutes).values(computed);
    }

    logger.info(
      `Computed ${computed.length} university commutes for user ${ownerId}`
    );
    return listUniversityCommutesForUser(ownerId);
  } catch (e) {
    logger.error('Error computing university commutes', e);
    throw e;
  }
};
