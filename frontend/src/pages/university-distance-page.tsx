import L, { type Map as LeafletMap, type Marker as LeafletMarker } from "leaflet"
import { useEffect, useMemo, useRef, useState } from "react"
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from "react-leaflet"

import { UniversityPanel } from "@/components/university/university-panel"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Skeleton } from "@/components/ui/skeleton"
import { captureAnalyticsEvent } from "@/lib/analytics"
import "@/lib/leaflet-icon-fix"
import { decodePolyline, routePathOptions } from "@/lib/polyline"
import { useSpacesWithStatus } from "@/lib/spaces"
import { useUniversityDashboard } from "@/lib/university"
import { formatDuration } from "@/lib/utils"
import { useIndexedRefs } from "@/lib/use-indexed-refs"

const CARTO_BASEMAP_KEY = import.meta.env.VITE_CARTO_BASEMAP_KEY?.trim()
const MADRID_CENTER: [number, number] = [40.4168, -3.7038]

const universityIcon = L.divIcon({
  className: "",
  html: '<span style="display:flex;width:28px;height:28px;align-items:center;justify-content:center;border-radius:9999px;background:var(--primary);color:var(--primary-foreground);border:3px solid var(--background);box-shadow:0 0 0 1px rgba(0,0,0,.2),0 3px 8px rgba(0,0,0,.3);font-size:15px">🎓</span>',
  iconSize: [28, 28],
  iconAnchor: [14, 14],
})

function FitMapToLocations({ points }: { points: [number, number][] }) {
  const map = useMap()

  useEffect(() => {
    if (points.length === 0) return
    if (points.length === 1) {
      map.setView(points[0], 14)
      return
    }
    map.fitBounds(points, { padding: [36, 36], maxZoom: 15 })
  }, [map, points])
  return null
}

export function UniversityDistancePage() {
  const {
    data,
    isPending: universityPending,
    isError: universityError,
    refetch: refetchUniversity,
  } = useUniversityDashboard()
  const {
    items: spaces,
    isLoading: spacesPending,
    isError: spacesError,
    refetch: refetchSpaces,
  } = useSpacesWithStatus()
  const [selectedSpaceId, setSelectedSpaceId] = useState<number | null>(null)
  const mapRef = useRef<LeafletMap | null>(null)
  const comparisonCapturedRef = useRef(false)
  const { refs: markerRefs, getRef: getMarkerRef } = useIndexedRefs<LeafletMarker>()

  const geocodedSpaces = useMemo(
    () => spaces.filter((space) => space.lat != null && space.lng != null),
    [spaces]
  )
  const commutesBySpaceId = useMemo(
    () => new Map((data?.commutes ?? []).map((commute) => [commute.spaceId, commute])),
    [data?.commutes]
  )
  const mapPoints = useMemo<[number, number][]>(() => {
    const points = geocodedSpaces.map((space) => [space.lat!, space.lng!] as [number, number])
    if (data?.university) points.push([data.university.lat, data.university.lng])
    return points
  }, [data, geocodedSpaces])
  const selectedCommute =
    selectedSpaceId == null ? undefined : commutesBySpaceId.get(selectedSpaceId)
  const selectedRouteLegs = useMemo(
    () =>
      (selectedCommute?.routeGeometry ?? [])
        .map((leg, index) => ({
          key: `${index}-${leg.mode}`,
          mode: leg.mode,
          points: decodePolyline(leg.encodedPolyline),
        }))
        .filter((leg) => leg.points.length > 1),
    [selectedCommute]
  )
  const selectedRoutePoints = useMemo(
    () => selectedRouteLegs.flatMap((leg) => leg.points),
    [selectedRouteLegs]
  )

  useEffect(() => {
    if (
      comparisonCapturedRef.current ||
      universityPending ||
      universityError ||
      spacesPending ||
      spacesError ||
      !data?.university ||
      geocodedSpaces.length === 0
    ) {
      return
    }

    captureAnalyticsEvent("university_comparison_viewed", {
      eligible_space_count: geocodedSpaces.length,
      commute_count: data.commutes.length,
    })
    comparisonCapturedRef.current = true
  }, [data, geocodedSpaces.length, spacesError, spacesPending, universityError, universityPending])

  useEffect(() => {
    if (selectedSpaceId == null || !mapRef.current) return
    const space = geocodedSpaces.find((item) => item.id === selectedSpaceId)
    if (!space) return
    if (selectedRoutePoints.length > 1) {
      mapRef.current.fitBounds(selectedRoutePoints, { padding: [48, 48], maxZoom: 16 })
    } else {
      mapRef.current.panTo([space.lat!, space.lng!])
    }
    markerRefs.current.get(selectedSpaceId)?.openPopup()
  }, [geocodedSpaces, markerRefs, selectedRoutePoints, selectedSpaceId])

  if (universityPending || spacesPending) {
    return (
      <div className="grid h-full grid-cols-[minmax(24rem,42%)_1fr]">
        <div className="space-y-3 border-r border-border p-6">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
        <Skeleton className="h-full w-full rounded-none" />
      </div>
    )
  }

  if (universityError || spacesError) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
        <p className="text-sm text-muted-foreground">
          Couldn&apos;t load the university commute dashboard.
        </p>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            void refetchUniversity()
            void refetchSpaces()
          }}
        >
          Try again
        </Button>
      </div>
    )
  }

  return (
    <ResizablePanelGroup className="h-full">
      <ResizablePanel defaultSize="48" minSize="32" maxSize="68">
        <UniversityPanel
          university={data?.university ?? null}
          commutes={data?.commutes ?? []}
          spaces={spaces}
          selectedSpaceId={selectedSpaceId}
          onSelectSpace={setSelectedSpaceId}
        />
      </ResizablePanel>
      <ResizableHandle withHandle />
      <ResizablePanel
        defaultSize="52"
        minSize="28"
        onResize={() => mapRef.current?.invalidateSize()}
      >
        <MapContainer ref={mapRef} center={MADRID_CENTER} zoom={12} className="h-full w-full">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            maxZoom={20}
            subdomains="abcd"
            url={`https://{s}.basemaps.cartocdn.com/rastertiles/light_all/{z}/{x}/{y}.png?key=${encodeURIComponent(CARTO_BASEMAP_KEY)}`}
          />
          <FitMapToLocations points={mapPoints} />

          {selectedRouteLegs.map((leg) => (
            <Polyline
              key={leg.key}
              positions={leg.points}
              pathOptions={routePathOptions(leg.mode)}
            />
          ))}

          {data?.university && (
            <Marker position={[data.university.lat, data.university.lng]} icon={universityIcon}>
              <Popup>
                <span className="font-medium">{data.university.name}</span>
                <br />
                {data.university.address}
              </Popup>
            </Marker>
          )}

          {geocodedSpaces.map((space) => {
            const commute = commutesBySpaceId.get(space.id)
            return (
              <Marker
                key={space.id}
                position={[space.lat!, space.lng!]}
                ref={getMarkerRef(space.id)}
                eventHandlers={{ click: () => setSelectedSpaceId(space.id) }}
                zIndexOffset={selectedSpaceId === space.id ? 1000 : 0}
              >
                <Popup>
                  <span className="font-medium">{space.name}</span>
                  <br />
                  {commute
                    ? `${formatDuration(commute.durationSeconds)} to university`
                    : "Commute not calculated"}
                </Popup>
              </Marker>
            )
          })}
        </MapContainer>
      </ResizablePanel>
    </ResizablePanelGroup>
  )
}
