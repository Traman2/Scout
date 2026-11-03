import { Clock3Icon, ExternalLinkIcon, FootprintsIcon, MapPinIcon, TrainFrontIcon, XIcon } from "lucide-react"

import { Button, buttonVariants } from "@/components/ui/button"
import type { Poi, Space, SpaceRoute } from "@/lib/spaces"
import { cn, formatDuration } from "@/lib/utils"

interface PoiDetailCardProps {
  poi: Poi
  space: Space
  route?: SpaceRoute
  routesPending: boolean
  routesError: boolean
  onClose: () => void
}

function straightLineDistance(space: Space, poi: Poi) {
  if (space.lat == null || space.lng == null) return null
  const radians = (degrees: number) => degrees * Math.PI / 180
  const a = Math.sin(radians(poi.lat - space.lat) / 2) ** 2
    + Math.cos(radians(space.lat)) * Math.cos(radians(poi.lat))
    * Math.sin(radians(poi.lng - space.lng) / 2) ** 2
  const meters = 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)))
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`
}

export function PoiDetailCard({ poi, space, route, routesPending, routesError, onClose }: PoiDetailCardProps) {
  const distance = straightLineDistance(space, poi)
  const computedAt = route ? new Date(route.computed_at) : null
  const hasComputedAt = computedAt != null && Number.isFinite(computedAt.getTime())
  const transitSeconds = route
    ? route.metroDurationSeconds + route.busDurationSeconds + route.tramDurationSeconds + route.otherDurationSeconds
    : undefined
  const metrics = [
    { label: "Walking", seconds: route?.walkDurationSeconds, icon: FootprintsIcon },
    { label: "Transit", seconds: transitSeconds, icon: TrainFrontIcon },
    { label: "Waiting", seconds: route?.waitDurationSeconds, icon: Clock3Icon },
  ]

  return (
    <section aria-label="Selected place details" className="relative rounded-xl border border-border bg-card text-card-foreground shadow-lg">
      <Button variant="ghost" size="icon-xs" onClick={onClose} aria-label="Close place details" className="absolute top-2 right-2">
        <XIcon />
      </Button>
      <div className="flex items-start justify-between gap-3 p-4">
        <div className="min-w-0">
          <p className="mb-1 flex items-center gap-1.5 text-xs text-muted-foreground">
            <span className={cn("size-2 shrink-0 rounded-full", poi.category === "transit" ? "bg-info" : "bg-warning")} />
            <span className="capitalize">{poi.category.replaceAll("_", " ")}</span>
          </p>
          <h3 className="font-heading text-sm leading-snug font-semibold break-words">{poi.name}</h3>
          {distance && (
            <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
              <MapPinIcon className="size-3 shrink-0" />
              {distance} straight-line distance
            </p>
          )}
        </div>
        {route && (
          <div className="shrink-0 pr-5 text-right">
            <p className="text-xl font-semibold tracking-tight tabular-nums">{formatDuration(route.durationSeconds)}</p>
            <p className="text-xs text-muted-foreground">Total travel</p>
          </div>
        )}
      </div>
      {route ? (
        <dl className="mx-4 grid grid-cols-3 gap-2 rounded-lg bg-muted/60 p-3">
          {metrics.map(({ label, seconds, icon: Icon }) => (
            <div key={label}>
              <dt className="flex items-center gap-1 text-xs text-muted-foreground"><Icon className="size-3 shrink-0" />{label}</dt>
              <dd className="mt-1 text-sm font-medium tabular-nums">{formatDuration(seconds)}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p role="status" className="mx-4 rounded-lg bg-muted/60 p-3 text-xs text-muted-foreground">
          {routesPending ? "Loading travel times…" : routesError ? "Travel times could not be loaded." : "No route calculated for this place yet."}
        </p>
      )}
      <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
        <p className="text-xs text-muted-foreground">
          {hasComputedAt ? <>Calculated <time dateTime={route!.computed_at} title={computedAt.toLocaleString()}>{computedAt.toLocaleString(undefined, { month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit" })}</time></> : "Calculation time unavailable"}
        </p>
        <a
          href={`https://www.openstreetmap.org/?mlat=${poi.lat}&mlon=${poi.lng}#map=18/${poi.lat}/${poi.lng}`}
          target="_blank"
          rel="noopener noreferrer"
          className={buttonVariants({ variant: "outline", size: "sm" })}
          aria-label={`Open ${poi.name} in maps (opens in a new tab)`}
        >
          Open in maps <ExternalLinkIcon className="size-3.5" />
        </a>
      </div>
    </section>
  )
}
