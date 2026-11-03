import {
  pgTable,
  serial,
  bigint,
  varchar,
  doublePrecision,
  timestamp,
} from 'drizzle-orm/pg-core';

// Shared across all spaces — the same OSM node (a given supermarket, bus
// stop, etc.) is stored once and linked to every nearby space via
// space-poi.model.ts, not duplicated per space.
export const pois = pgTable('pois', {
  id: serial('id').primaryKey(),
  osmId: bigint('osm_id', { mode: 'number' }).notNull().unique(),
  category: varchar('category', { length: 50 }).notNull(),
  name: varchar('name', { length: 255 }).notNull(),
  lat: doublePrecision('lat').notNull(),
  lng: doublePrecision('lng').notNull(),
  created_at: timestamp().defaultNow().notNull(),
});
