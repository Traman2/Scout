import { GraduationCapIcon, Loader2Icon, PencilIcon, RouteIcon } from "lucide-react"
import { useMemo, useState } from "react"
import { toast } from "sonner"

import { ScoreBadge } from "@/components/spaces/score-badge"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ApiError } from "@/lib/api-client"
import type { SpaceWithStatus } from "@/lib/spaces"
import {
  useComputeUniversityCommutes,
  type University,
  type UniversityCommute,
} from "@/lib/university"
import { cn, formatDuration } from "@/lib/utils"
import { UniversityForm } from "./university-form"

interface UniversityPanelProps {
  university: University | null
  commutes: UniversityCommute[]
  spaces: SpaceWithStatus[]
  selectedSpaceId: number | null
  onSelectSpace: (spaceId: number) => void
}

export function UniversityPanel({
  university,
  commutes,
  spaces,
  selectedSpaceId,
  onSelectSpace,
}: UniversityPanelProps) {
  const [editing, setEditing] = useState(false)
  const computeCommutes = useComputeUniversityCommutes()
  const commutesBySpaceId = useMemo(
    () => new Map(commutes.map((commute) => [commute.spaceId, commute])),
    [commutes]
  )
  const hasStoredGeometry = commutes.some((commute) => (commute.routeGeometry?.length ?? 0) > 0)

  const handleCompute = () => {
    computeCommutes.mutate(undefined, {
      onSuccess: ({ commutes: results }) => {
        toast.success(
          results.length === 1
            ? "Calculated 1 university commute"
            : `Calculated ${results.length} university commutes`
        )
      },
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Could not calculate university commutes."
        )
      },
    })
  }

  if (!university || editing) {
    return (
      <div className="flex h-full items-center justify-center overflow-y-auto px-6 py-8">
        <div className="w-full max-w-md">
          <div className="mb-6 flex size-12 items-center justify-center rounded-full bg-muted">
            <GraduationCapIcon className="size-6 text-muted-foreground" />
          </div>
          <h1 className="font-heading text-xl font-semibold">
            {university ? "Edit university" : "Add your university"}
          </h1>
          <p className="mt-1 mb-6 text-sm text-muted-foreground">
            We&apos;ll geocode this address and calculate each apartment&apos;s door-to-door commute
            with the Madrid transit network.
          </p>
          <UniversityForm
            key={`${university?.id ?? "new"}-${university?.updated_at ?? ""}`}
            university={university}
            onCancel={university ? () => setEditing(false) : undefined}
            onSaved={() => setEditing(false)}
          />
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col">
      <div className="shrink-0 border-b border-border px-6 py-4">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <GraduationCapIcon className="size-4 shrink-0 text-primary" />
              <h1 className="truncate font-heading text-lg font-semibold">{university.name}</h1>
            </div>
            <p className="mt-1 text-sm text-muted-foreground">{university.address}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
            <PencilIcon className="size-3.5" />
            Edit
          </Button>
        </div>

        <div className="mt-4 flex items-center justify-between gap-3">
          <p className="text-xs text-muted-foreground">
            {commutes.length > 0 && !hasStoredGeometry
              ? "Recalculate commutes to load route lines."
              : commutes.length > 0
                ? `${commutes.length} of ${spaces.filter((space) => space.lat != null && space.lng != null).length} commutes calculated`
                : "Commutes have not been calculated yet."}
          </p>
          <Button size="sm" onClick={handleCompute} disabled={computeCommutes.isPending}>
            {computeCommutes.isPending ? (
              <Loader2Icon className="size-3.5 animate-spin" />
            ) : (
              <RouteIcon className="size-3.5" />
            )}
            {computeCommutes.isPending ? "Calculating…" : "Calculate commutes"}
          </Button>
        </div>
      </div>

      <div className="flex-1 overflow-auto px-6 py-4">
        {spaces.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Add an apartment on the Spaces page to compare its commute.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Apartment</TableHead>
                <TableHead>Score</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Walk</TableHead>
                <TableHead>Transit</TableHead>
                <TableHead>Metro</TableHead>
                <TableHead>Bus</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {spaces.map((space) => {
                const commute = commutesBySpaceId.get(space.id)
                const transitSeconds = commute
                  ? commute.metroDurationSeconds +
                    commute.busDurationSeconds +
                    commute.tramDurationSeconds +
                    commute.otherDurationSeconds
                  : null

                return (
                  <TableRow
                    key={space.id}
                    className={cn(
                      space.lat != null && space.lng != null && "cursor-pointer",
                      selectedSpaceId === space.id && "bg-accent"
                    )}
                    onClick={() => {
                      if (space.lat != null && space.lng != null) onSelectSpace(space.id)
                    }}
                  >
                    <TableCell className="min-w-44 whitespace-normal">
                      <p className="font-medium">{space.name}</p>
                      <p className="line-clamp-1 text-xs text-muted-foreground">
                        {space.lat == null || space.lng == null ? "Not geocoded" : space.address}
                      </p>
                    </TableCell>
                    <TableCell>
                      <ScoreBadge score={space.score.score} />
                    </TableCell>
                    <TableCell>{formatDuration(commute?.durationSeconds)}</TableCell>
                    <TableCell>{formatDuration(commute?.walkDurationSeconds)}</TableCell>
                    <TableCell>{formatDuration(transitSeconds)}</TableCell>
                    <TableCell>{formatDuration(commute?.metroDurationSeconds)}</TableCell>
                    <TableCell>{formatDuration(commute?.busDurationSeconds)}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  )
}
