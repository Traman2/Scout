import type { Map as LeafletMap } from "leaflet"
import { useEffect, useMemo, useRef, useState } from "react"
import { useParams } from "react-router-dom"
import { MapContainer, Marker, Polyline, Popup, TileLayer } from "react-leaflet"

import { SetCoordinatesForm } from "@/components/spaces/set-coordinates-form"
import { getPoiCategoryGroup, getPoiIcon } from "@/components/spaces/poi-icon"
import { SpaceDetailPanel } from "@/components/spaces/space-detail-panel"
import { PoiDetailCard } from "@/components/spaces/poi-detail-card"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Skeleton } from "@/components/ui/skeleton"
import { ApiError } from "@/lib/api-client"
import "@/lib/leaflet-icon-fix"
import { decodePolyline, routePathOptions } from "@/lib/polyline"
import { useSpace, useSpacePois, useSpaceRoutes } from "@/lib/spaces"

const CARTO_BASEMAP_KEY = import.meta.env.VITE_CARTO_BASEMAP_KEY?.trim()

export function SpaceDetailPage() {
  const { spaceId } = useParams<{ spaceId: string }>()
  // Keying by spaceId forces a full remount (and state reset — selection,
  // toggles, pagination) when navigating directly from one space detail
  // page to another, since React Router otherwise reuses this component
  // instance across `/spaces/:spaceId` matches.
  return <SpaceDetailPageContent key={spaceId} spaceId={spaceId} />
}

function SpaceDetailPageContent({ spaceId }: { spaceId: string | undefined }) {
  const id = Number(spaceId)
  const { data: space, isPending, error } = useSpace(id)
  const { data: pois } = useSpacePois(id)
  const { data: routes, isPending: routesPending, isError: routesError } = useSpaceRoutes(id)
  const [showTransit, setShowTransit] = useState(true)
  const [showAmenities, setShowAmenities] = useState(true)
  const [selectedPoiId, setSelectedPoiId] = useState<number | null>(null)
  const mapRef = useRef<LeafletMap | null>(null)
  const selectedPoi = pois?.find((poi) => poi.id === selectedPoiId)

  const visiblePois = useMemo(() => {
    return (pois ?? []).filter((poi) => {
      const group = getPoiCategoryGroup(poi.category)
      return group === "transit" ? showTransit : showAmenities
    })
  }, [pois, showTransit, showAmenities])
  const routesByPoiId = useMemo(
    () => new Map((routes ?? []).map((route) => [route.poiId, route])),
    [routes]
  )
  const selectedRoute = selectedPoiId == null ? undefined : routesByPoiId.get(selectedPoiId)
  const selectedRouteLegs = useMemo(
    () =>
      (selectedRoute?.routeGeometry ?? [])
        .map((leg, index) => ({
          key: `${index}-${leg.mode}`,
          mode: leg.mode,
          points: decodePolyline(leg.encodedPolyline),
        }))
        .filter((leg) => leg.points.length > 1),
    [selectedRoute]
  )
  const selectedRoutePoints = useMemo(
    () => selectedRouteLegs.flatMap((leg) => leg.points),
    [selectedRouteLegs]
  )

  // Selecting a POI should always make it visible on the map, even if its
  // category is currently toggled off — otherwise the map pans to an empty
  // spot with no marker to focus.
  const handleSelectPoi = (poiId: number) => {
    const poi = pois?.find((p) => p.id === poiId)
    if (poi) {
      const group = getPoiCategoryGroup(poi.category)
      if (group === "transit") setShowTransit(true)
      else setShowAmenities(true)
    }
    setSelectedPoiId(poiId)
  }

  // Keep the map focused on whichever POI is currently selected, whether
  // selection came from clicking a marker or a table row.
  useEffect(() => {
    if (selectedPoiId == null || !pois || !mapRef.current) return

    const poi = pois.find((p) => p.id === selectedPoiId)
    if (!poi) return

    if (selectedRoutePoints.length > 1) {
      mapRef.current.fitBounds(selectedRoutePoints, {
        paddingTopLeft: [48, 48],
        paddingBottomRight: [48, Math.min(320, mapRef.current.getSize().y * 0.55)],
        maxZoom: 17,
      })
    } else {
      mapRef.current.panTo([poi.lat, poi.lng])
    }
  }, [selectedPoiId, pois, selectedRoutePoints])

  if (isPending) {
    return (
      <div className="flex h-full items-center justify-center">
        <Skeleton className="h-10 w-64" />
      </div>
    )
  }

  if (error instanceof ApiError && error.status === 404) {
    return (
      <div className="flex h-full items-center justify-center px-6">
        <div className="rounded-lg border border-dashed border-border px-12 py-16 text-center">
          <p className="text-sm text-muted-foreground">Space not found.</p>
        </div>
      </div>
    )
  }

  if (!space) return null

  if (space.lat == null || space.lng == null) {
    return (
      <div className="flex h-full items-center justify-center px-6">
        <SetCoordinatesForm spaceId={space.id} />
      </div>
    )
  }

  return (
    <ResizablePanelGroup className="h-full">
      <ResizablePanel defaultSize="42" minSize="25" maxSize="60">
        <SpaceDetailPanel
          space={space}
          showTransit={showTransit}
          showAmenities={showAmenities}
          onShowTransitChange={setShowTransit}
          onShowAmenitiesChange={setShowAmenities}
          selectedPoiId={selectedPoiId}
          onSelectPoi={handleSelectPoi}
        />
      </ResizablePanel>
      <ResizableHandle withHandle />
      <ResizablePanel
        defaultSize="58"
        minSize="30"
        onResize={() => mapRef.current?.invalidateSize()}
      >
        <div className="relative isolate h-full w-full">
        <MapContainer
          key={space.id}
          ref={mapRef}
          center={[space.lat, space.lng]}
          zoom={15}
          className="z-0 h-full w-full"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            maxZoom={20}
            subdomains="abcd"
            url={`https://{s}.basemaps.cartocdn.com/rastertiles/light_all/{z}/{x}/{y}.png?key=${encodeURIComponent(CARTO_BASEMAP_KEY)}`}
          />
          {selectedRouteLegs.map((leg) => (
            <Polyline
              key={leg.key}
              positions={leg.points}
              pathOptions={routePathOptions(leg.mode)}
            />
          ))}
          <Marker position={[space.lat, space.lng]}>
            <Popup>
              <span className="font-medium">{space.name}</span>
              <br />
              {space.address}
            </Popup>
          </Marker>
          {visiblePois.map((poi) => (
            <Marker
              key={poi.id}
              position={[poi.lat, poi.lng]}
              icon={getPoiIcon(poi.category, poi.id === selectedPoiId)}
              zIndexOffset={poi.id === selectedPoiId ? 1000 : 0}
              eventHandlers={{ click: () => handleSelectPoi(poi.id) }}
            />
          ))}
        </MapContainer>
        {selectedPoi && (
          <div className="absolute right-3 bottom-7 left-3 z-10 max-h-[55%] overflow-y-auto rounded-xl sm:left-auto sm:w-80 sm:max-w-[calc(100%-1.5rem)]">
            <PoiDetailCard
              poi={selectedPoi}
              space={space}
              route={selectedRoute}
              routesPending={routesPending}
              routesError={routesError}
              onClose={() => setSelectedPoiId(null)}
            />
          </div>
        )}
        </div>
      </ResizablePanel>
    </ResizablePanelGroup>
  )
}
