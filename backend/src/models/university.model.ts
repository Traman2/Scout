import {
  doublePrecision,
  integer,
  jsonb,
  pgTable,
  serial,
  text,
  timestamp,
  unique,
  varchar,
} from 'drizzle-orm/pg-core';
import { spaces } from '#models/space.model.ts';
import { users } from '#models/user.model.ts';

export interface UniversityRouteGeometryLeg {
  mode: string;
  encodedPolyline: string;
}

export const universities = pgTable('universities', {
  id: serial('id').primaryKey(),
  ownerId: integer('owner_id')
    .notNull()
    .unique()
    .references(() => users.id, { onDelete: 'cascade' }),
  name: varchar('name', { length: 255 }).notNull(),
  address: text('address').notNull(),
  lat: doublePrecision('lat').notNull(),
  lng: doublePrecision('lng').notNull(),
  created_at: timestamp().defaultNow().notNull(),
  updated_at: timestamp().defaultNow().notNull(),
});

export const universityCommutes = pgTable(
  'university_commutes',
  {
    id: serial('id').primaryKey(),
    universityId: integer('university_id')
      .notNull()
      .references(() => universities.id, { onDelete: 'cascade' }),
    spaceId: integer('space_id')
      .notNull()
      .references(() => spaces.id, { onDelete: 'cascade' }),
    durationSeconds: integer('duration_seconds').notNull(),
    walkDurationSeconds: integer('walk_duration_seconds').notNull(),
    metroDurationSeconds: integer('metro_duration_seconds').notNull(),
    busDurationSeconds: integer('bus_duration_seconds').notNull(),
    tramDurationSeconds: integer('tram_duration_seconds').notNull(),
    otherDurationSeconds: integer('other_duration_seconds').notNull(),
    waitDurationSeconds: integer('wait_duration_seconds').notNull(),
    routeGeometry:
      jsonb('route_geometry').$type<UniversityRouteGeometryLeg[]>(),
    computed_at: timestamp().defaultNow().notNull(),
  },
  table => [unique().on(table.universityId, table.spaceId)]
);
