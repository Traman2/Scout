import {
  pgTable,
  serial,
  integer,
  timestamp,
  unique,
} from 'drizzle-orm/pg-core';
import { spaces } from '#models/space.model.ts';
import { pois } from '#models/poi.model.ts';

// Many-to-many: which discovered POIs are "nearby" for which space. Rows
// here are what discoverPoisForSpace deletes-and-replaces per space; the
// pois rows themselves are shared and untouched by that.
export const spacePois = pgTable(
  'space_pois',
  {
    id: serial('id').primaryKey(),
    spaceId: integer('space_id')
      .notNull()
      .references(() => spaces.id, { onDelete: 'cascade' }),
    poiId: integer('poi_id')
      .notNull()
      .references(() => pois.id, { onDelete: 'cascade' }),
    created_at: timestamp().defaultNow().notNull(),
  },
  table => [unique().on(table.spaceId, table.poiId)]
);
