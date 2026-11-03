CREATE TABLE "route_progress" (
	"id" serial PRIMARY KEY NOT NULL,
	"space_id" integer NOT NULL,
	"status" varchar(20) NOT NULL,
	"completed" integer DEFAULT 0 NOT NULL,
	"total" integer DEFAULT 0 NOT NULL,
	"error" text,
	"started_at" timestamp DEFAULT now() NOT NULL,
	"finished_at" timestamp,
	"updated_at" timestamp DEFAULT now() NOT NULL,
	CONSTRAINT "route_progress_space_id_unique" UNIQUE("space_id")
);
--> statement-breakpoint
ALTER TABLE "route_progress" ADD CONSTRAINT "route_progress_space_id_spaces_id_fk" FOREIGN KEY ("space_id") REFERENCES "public"."spaces"("id") ON DELETE cascade ON UPDATE no action;