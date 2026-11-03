import {
  pgTable,
  serial,
  integer,
  varchar,
  text,
  timestamp,
} from 'drizzle-orm/pg-core';
import { spaces } from '#models/space.model.ts';

// One row per space, upserted on every computeRoutes run — tracks live
// progress of the batched OTP routing job so the frontend can reattach to
// an in-flight (or just-finished/failed) job after navigating away and back,
// or after a full page reload.
export const routeProgress = pgTable('route_progress', {
  id: serial('id').primaryKey(),
  spaceId: integer('space_id')
    .notNull()
    .unique()
    .references(() => spaces.id, { onDelete: 'cascade' }),
  status: varchar('status', { length: 20 }).notNull(), // 'in_progress' | 'done' | 'error'
  completed: integer('completed').notNull().default(0),
  total: integer('total').notNull().default(0),
  error: text('error'),
  started_at: timestamp().defaultNow().notNull(),
  finished_at: timestamp(),
  updated_at: timestamp().defaultNow().notNull(),
});
