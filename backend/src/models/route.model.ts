import {
  pgTable,
  serial,
  integer,
  jsonb,
  timestamp,
  unique,
} from 'drizzle-orm/pg-core';
import { spaces } from '#models/space.model.ts';
import { pois } from '#models/poi.model.ts';

export interface SpaceRouteGeometryLeg {
  mode: string;
  encodedPolyline: string;
}

// Unlike space_pois, routes is per-space, not shared — a walk+transit time is
// only meaningful from one specific space's coordinates. computeRoutesForSpace
// deletes-and-replaces all rows for a space on every recompute, same pattern
// as space_pois for discoverPoisForSpace.
export const routes = pgTable(
  'routes',
  {
    id: serial('id').primaryKey(),
    spaceId: integer('space_id')
      .notNull()
      .references(() => spaces.id, { onDelete: 'cascade' }),
    poiId: integer('poi_id')
      .notNull()
      .references(() => pois.id, { onDelete: 'cascade' }),
    durationSeconds: integer('duration_seconds').notNull(),
    walkDurationSeconds: integer('walk_duration_seconds').notNull(),
    metroDurationSeconds: integer('metro_duration_seconds').notNull(),
    busDurationSeconds: integer('bus_duration_seconds').notNull(),
    tramDurationSeconds: integer('tram_duration_seconds').notNull(),
    otherDurationSeconds: integer('other_duration_seconds').notNull(),
    waitDurationSeconds: integer('wait_duration_seconds').notNull(),
    routeGeometry: jsonb('route_geometry').$type<SpaceRouteGeometryLeg[]>(),
    computed_at: timestamp().defaultNow().notNull(),
  },
  table => [unique().on(table.spaceId, table.poiId)]
);
