import {
  pgTable,
  serial,
  integer,
  varchar,
  text,
  doublePrecision,
  timestamp,
} from 'drizzle-orm/pg-core';
import { users } from '#models/user.model.ts';

export const spaces = pgTable('spaces', {
  id: serial('id').primaryKey(),
  ownerId: integer('owner_id')
    .notNull()
    .references(() => users.id),
  name: varchar('name', { length: 255 }).notNull(),
  address: text('address').notNull(),
  lat: doublePrecision('lat'),
  lng: doublePrecision('lng'),
  created_at: timestamp().defaultNow().notNull(),
  updated_at: timestamp().defaultNow().notNull(),
});
