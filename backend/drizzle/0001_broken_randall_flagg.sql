CREATE TABLE "routes" (
	"id" serial PRIMARY KEY NOT NULL,
	"space_id" integer NOT NULL,
	"poi_id" integer NOT NULL,
	"duration_seconds" integer NOT NULL,
	"walk_duration_seconds" integer NOT NULL,
	"transit_duration_seconds" integer NOT NULL,
	"computed_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "routes_space_id_poi_id_unique" UNIQUE("space_id","poi_id")
);
--> statement-breakpoint
ALTER TABLE "routes" ADD CONSTRAINT "routes_space_id_spaces_id_fk" FOREIGN KEY ("space_id") REFERENCES "public"."spaces"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "routes" ADD CONSTRAINT "routes_poi_id_pois_id_fk" FOREIGN KEY ("poi_id") REFERENCES "public"."pois"("id") ON DELETE cascade ON UPDATE no action;