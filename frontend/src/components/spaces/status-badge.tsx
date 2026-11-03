/* eslint-disable react-refresh/only-export-components */
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import type { SpaceStatus } from "@/lib/spaces"

export const STATUS_META: Record<SpaceStatus, { label: string; className: string }> = {
  not_geocoded: {
    label: "Not Geocoded",
    className: "bg-muted text-muted-foreground",
  },
  geocoded: {
    label: "Geocoded",
    className: "bg-info text-info-foreground",
  },
  pois_discovered: {
    label: "POIs Discovered",
    className: "bg-warning text-warning-foreground",
  },
  routes_computed: {
    label: "Routes Computed",
    className: "bg-success text-success-foreground",
  },
}

export const STATUS_PIPELINE: SpaceStatus[] = [
  "not_geocoded",
  "geocoded",
  "pois_discovered",
  "routes_computed",
]

interface StatusBadgeProps {
  status: SpaceStatus
  className?: string
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const meta = STATUS_META[status]
  return (
    <Badge variant="outline" className={cn("border-transparent", meta.className, className)}>
      {meta.label}
    </Badge>
  )
}
