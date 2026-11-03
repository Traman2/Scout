CREATE TABLE "pois" (
	"id" serial PRIMARY KEY NOT NULL,
	"osm_id" bigint NOT NULL,
	"category" varchar(50) NOT NULL,
	"name" varchar(255) NOT NULL,
	"lat" double precision NOT NULL,
	"lng" double precision NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "pois_osm_id_unique" UNIQUE("osm_id")
);
--> statement-breakpoint
CREATE TABLE "space_pois" (
	"id" serial PRIMARY KEY NOT NULL,
	"space_id" integer NOT NULL,
	"poi_id" integer NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "space_pois_space_id_poi_id_unique" UNIQUE("space_id","poi_id")
);
--> statement-breakpoint
CREATE TABLE "spaces" (
	"id" serial PRIMARY KEY NOT NULL,
	"owner_id" integer NOT NULL,
	"name" varchar(255) NOT NULL,
	"address" text NOT NULL,
	"lat" double precision,
	"lng" double precision,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" serial PRIMARY KEY NOT NULL,
	"name" varchar(255) NOT NULL,
	"email" varchar(255) NOT NULL,
	"password" varchar(255) NOT NULL,
	"role" varchar(50) DEFAULT 'user' NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "users_email_unique" UNIQUE("email")
);
--> statement-breakpoint
ALTER TABLE "space_pois" ADD CONSTRAINT "space_pois_space_id_spaces_id_fk" FOREIGN KEY ("space_id") REFERENCES "public"."spaces"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "space_pois" ADD CONSTRAINT "space_pois_poi_id_pois_id_fk" FOREIGN KEY ("poi_id") REFERENCES "public"."pois"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "spaces" ADD CONSTRAINT "spaces_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;