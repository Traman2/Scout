CREATE TABLE "universities" (
	"id" serial PRIMARY KEY NOT NULL,
	"owner_id" integer NOT NULL,
	"name" varchar(255) NOT NULL,
	"address" text NOT NULL,
	"lat" double precision NOT NULL,
	"lng" double precision NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "universities_owner_id_unique" UNIQUE("owner_id")
);
--> statement-breakpoint
CREATE TABLE "university_commutes" (
	"id" serial PRIMARY KEY NOT NULL,
	"university_id" integer NOT NULL,
	"space_id" integer NOT NULL,
	"duration_seconds" integer NOT NULL,
	"walk_duration_seconds" integer NOT NULL,
	"metro_duration_seconds" integer NOT NULL,
	"bus_duration_seconds" integer NOT NULL,
	"tram_duration_seconds" integer NOT NULL,
	"other_duration_seconds" integer NOT NULL,
	"wait_duration_seconds" integer NOT NULL,
	"computed_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "university_commutes_university_id_space_id_unique" UNIQUE("university_id","space_id")
);
--> statement-breakpoint
ALTER TABLE "universities" ADD CONSTRAINT "universities_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "university_commutes" ADD CONSTRAINT "university_commutes_university_id_universities_id_fk" FOREIGN KEY ("university_id") REFERENCES "public"."universities"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "university_commutes" ADD CONSTRAINT "university_commutes_space_id_spaces_id_fk" FOREIGN KEY ("space_id") REFERENCES "public"."spaces"("id") ON DELETE cascade ON UPDATE no action;