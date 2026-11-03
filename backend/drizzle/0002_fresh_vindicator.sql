ALTER TABLE "routes" RENAME COLUMN "transit_duration_seconds" TO "metro_duration_seconds";--> statement-breakpoint
ALTER TABLE "routes" ADD COLUMN "bus_duration_seconds" integer NOT NULL;--> statement-breakpoint
ALTER TABLE "routes" ADD COLUMN "tram_duration_seconds" integer NOT NULL;--> statement-breakpoint
ALTER TABLE "routes" ADD COLUMN "other_duration_seconds" integer NOT NULL;