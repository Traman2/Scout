import { SearchIcon } from "lucide-react"
import { useEffect, useMemo, useState } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { cn, formatDuration } from "@/lib/utils"
import { useSpacePois, useSpaceRoutes, type Space } from "@/lib/spaces"
import { useIndexedRefs } from "@/lib/use-indexed-refs"
import { computeScore } from "@/lib/scoring"
import { ScoreBadge } from "./score-badge"

const PAGE_SIZE = 25

function matchesSearch(name: string, category: string, query: string) {
  const q = query.trim().toLowerCase()
  return !q || name.toLowerCase().includes(q) || category.toLowerCase().includes(q)
}

interface SpaceDetailPanelProps {
  space: Space
  showTransit: boolean
  showAmenities: boolean
  onShowTransitChange: (value: boolean) => void
  onShowAmenitiesChange: (value: boolean) => void
  selectedPoiId: number | null
  onSelectPoi: (id: number) => void
}

export function SpaceDetailPanel({
  space,
  showTransit,
  showAmenities,
  onShowTransitChange,
  onShowAmenitiesChange,
  selectedPoiId,
  onSelectPoi,
}: SpaceDetailPanelProps) {
  const { data: pois, isPending: poisPending } = useSpacePois(space.id)
  const { data: routes } = useSpaceRoutes(space.id)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState("")
  const { refs: rowRefs, getRef: getRowRef } = useIndexedRefs<HTMLTableRowElement>()

  const routesByPoiId = useMemo(() => {
    return new Map((routes ?? []).map((route) => [route.poiId, route]))
  }, [routes])

  const score = useMemo(
    () => computeScore(pois?.length ?? 0, routes ?? []),
    [pois, routes]
  )

  const filteredPois = useMemo(() => {
    if (!pois) return pois
    return pois.filter((poi) => matchesSearch(poi.name, poi.category, search))
  }, [pois, search])

  // A POI selected from the map might live on a different page — or be
  // hidden by the current search — jump/clear whatever's needed to show
  // it. This adjusts state during render (React's documented pattern for
  // this) rather than in an effect, so selection lands correctly in one
  // render instead of flashing the old page/search first.
  const [lastSelectedPoiId, setLastSelectedPoiId] = useState(selectedPoiId)
  let effectivePage = page
  if (selectedPoiId !== lastSelectedPoiId) {
    setLastSelectedPoiId(selectedPoiId)
    if (selectedPoiId != null && pois) {
      const selectedPoi = pois.find((poi) => poi.id === selectedPoiId)
      const stillMatches =
        selectedPoi && matchesSearch(selectedPoi.name, selectedPoi.category, search)

      if (!stillMatches) setSearch("")

      const displayedList = stillMatches ? (filteredPois ?? pois) : pois
      const index = displayedList.findIndex((poi) => poi.id === selectedPoiId)
      if (index !== -1) {
        effectivePage = Math.floor(index / PAGE_SIZE) + 1
        setPage(effectivePage)
      }
    }
  }

  const totalPages = Math.max(1, Math.ceil((filteredPois?.length ?? 0) / PAGE_SIZE))
  const currentPage = Math.min(effectivePage, totalPages)
  const paginatedPois = (filteredPois ?? []).slice(
    (currentPage - 1) * PAGE_SIZE,
    currentPage * PAGE_SIZE
  )

  // Once the right page is showing, scroll the selected row into view.
  useEffect(() => {
    if (selectedPoiId == null) return
    rowRefs.current.get(selectedPoiId)?.scrollIntoView({ block: "nearest", behavior: "smooth" })
  }, [selectedPoiId, currentPage, rowRefs])

  return (
    <div className="flex h-full flex-col">
      <div className="shrink-0 border-b border-border px-6 py-4">
        <div className="flex items-start justify-between gap-3">
          <h2 className="font-heading text-lg font-semibold">{space.name}</h2>
          <ScoreBadge score={score.score} className="mt-0.5 shrink-0" />
        </div>
        <p className="text-sm text-muted-foreground">{space.address}</p>
        <p className="text-sm text-muted-foreground">
          {space.lat?.toFixed(6)}, {space.lng?.toFixed(6)}
        </p>
        {score.score != null && (
          <dl className="mt-3 grid grid-cols-3 gap-3 text-xs">
            <div>
              <dt className="text-muted-foreground">Nearest grocery</dt>
              <dd className="font-medium">{formatDuration(score.nearestGroceryWalkSeconds)} walk</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Nearest gym</dt>
              <dd className="font-medium">{formatDuration(score.nearestGymWalkSeconds)} walk</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Nearest transit</dt>
              <dd className="font-medium">{formatDuration(score.nearestTransitWalkSeconds)} walk</dd>
            </div>
          </dl>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-6 border-b border-border px-6 py-4">
        <div className="flex items-center gap-2">
          <Switch
            id="show-transit"
            checked={showTransit}
            onCheckedChange={onShowTransitChange}
          />
          <Label htmlFor="show-transit">Transit</Label>
        </div>
        <div className="flex items-center gap-2">
          <Switch
            id="show-amenities"
            checked={showAmenities}
            onCheckedChange={onShowAmenitiesChange}
          />
          <Label htmlFor="show-amenities">Amenities</Label>
        </div>
      </div>

      {pois && pois.length > 0 && (
        <div className="shrink-0 border-b border-border px-6 py-4">
          <div className="relative">
            <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(1)
              }}
              placeholder="Search POIs…"
              className="pl-8"
            />
          </div>
        </div>
      )}

      <div className="min-h-0 flex-1 overflow-y-auto px-6 py-4">
        {poisPending ? (
          <div className="space-y-2">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : !pois || pois.length === 0 ? (
          <p className="text-sm text-muted-foreground">No POIs discovered yet.</p>
        ) : paginatedPois.length === 0 ? (
          <p className="text-sm text-muted-foreground">No POIs match your search.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Category</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Walk</TableHead>
                <TableHead>Metro</TableHead>
                <TableHead>Bus</TableHead>
                <TableHead>Tram</TableHead>
                <TableHead>Other</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {paginatedPois.map((poi) => {
                const route = routesByPoiId.get(poi.id)
                const isSelected = poi.id === selectedPoiId
                return (
                  <TableRow
                    key={poi.id}
                    ref={getRowRef(poi.id)}
                    onClick={() => onSelectPoi(poi.id)}
                    className={cn("cursor-pointer", isSelected && "bg-accent")}
                  >
                    <TableCell className="font-medium whitespace-normal">
                      <button
                        type="button"
                        onClick={(event) => {
                          event.stopPropagation()
                          onSelectPoi(poi.id)
                        }}
                        aria-pressed={isSelected}
                        aria-label={`Show details for ${poi.name}`}
                        className="rounded-sm text-left underline-offset-4 outline-none hover:underline focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        {poi.name}
                      </button>
                    </TableCell>
                    <TableCell className="text-muted-foreground capitalize">
                      {poi.category}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDuration(route?.durationSeconds)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDuration(route?.walkDurationSeconds)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDuration(route?.metroDurationSeconds)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDuration(route?.busDurationSeconds)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDuration(route?.tramDurationSeconds)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDuration(route?.otherDurationSeconds)}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex shrink-0 items-center justify-between border-t border-border px-6 py-3">
          <span className="text-sm text-muted-foreground">
            Page {currentPage} of {totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
