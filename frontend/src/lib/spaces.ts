import { useEffect } from "react"
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query"
import { io } from "socket.io-client"

import { apiClient, API_URL } from "./api-client"
import { computeScore, type SpaceScore } from "./scoring"

export interface Space {
  id: number
  ownerId: number
  name: string
  address: string
  lat: number | null
  lng: number | null
  created_at: string
  updated_at: string
}

export interface Poi {
  id: number
  osmId: number
  category: string
  name: string
  lat: number
  lng: number
  created_at: string
}

export interface SpaceRoute {
  id: number
  poiId: number
  poiName: string
  poiCategory: string
  durationSeconds: number
  walkDurationSeconds: number
  metroDurationSeconds: number
  busDurationSeconds: number
  tramDurationSeconds: number
  otherDurationSeconds: number
  waitDurationSeconds: number
  routeGeometry: { mode: string; encodedPolyline: string }[] | null
  computed_at: string
}

export type RouteProgressStatus = "idle" | "in_progress" | "done" | "error"

export interface RouteProgress {
  status: RouteProgressStatus
  completed: number
  total: number
  error: string | null
  startedAt: string | null
  finishedAt: string | null
}

export type SpaceStatus = "not_geocoded" | "geocoded" | "pois_discovered" | "routes_computed"

export function deriveStatus(space: Space, pois: Poi[], routes: SpaceRoute[]): SpaceStatus {
  if (space.lat == null || space.lng == null) return "not_geocoded"
  if (pois.length === 0) return "geocoded"
  if (routes.length === 0) return "pois_discovered"
  return "routes_computed"
}

export const spacesKey = ["spaces"] as const
export const spaceKey = (id: number) => ["spaces", id] as const
export const spacePoisKey = (id: number) => ["spaces", id, "pois"] as const
export const spaceRoutesKey = (id: number) => ["spaces", id, "routes"] as const
export const spaceRouteProgressKey = (id: number) => ["spaces", id, "route-progress"] as const

export function useSpaces() {
  return useQuery({
    queryKey: spacesKey,
    queryFn: async () => {
      const res = await apiClient.get<{ spaces: Space[] }>("/api/spaces")
      return res.spaces
    },
  })
}

export function useSpace(id: number) {
  return useQuery({
    queryKey: spaceKey(id),
    queryFn: async () => {
      const res = await apiClient.get<{ space: Space }>(`/api/spaces/${id}`)
      return res.space
    },
  })
}

export function useSpacePois(id: number) {
  return useQuery({
    queryKey: spacePoisKey(id),
    queryFn: async () => {
      const res = await apiClient.get<{ pois: Poi[] }>(`/api/spaces/${id}/pois`)
      return res.pois
    },
  })
}

export function useSpaceRoutes(id: number) {
  return useQuery({
    queryKey: spaceRoutesKey(id),
    queryFn: async () => {
      const res = await apiClient.get<{ routes: SpaceRoute[] }>(`/api/spaces/${id}/routes`)
      return res.routes
    },
  })
}

export function useCreateSpace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; address: string }) =>
      apiClient.post<{ message: string; space: Space }>("/api/spaces", data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: spacesKey })
    },
  })
}

export function useSetSpaceCoordinates(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { lat: number; lng: number }) =>
      apiClient.patch<{ message: string; space: Space }>(`/api/spaces/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: spaceKey(id) })
      queryClient.invalidateQueries({ queryKey: spacesKey })
    },
  })
}

export function useDiscoverPois(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post<{ message: string; pois: Poi[] }>(`/api/spaces/${id}/discover`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: spacePoisKey(id) })
      queryClient.invalidateQueries({ queryKey: spacesKey })
    },
  })
}

export function useGeocodeSpace(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post<{ message: string; space: Space }>(`/api/spaces/${id}/geocode`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: spaceKey(id) })
      queryClient.invalidateQueries({ queryKey: spacesKey })
    },
  })
}

export function useDeleteSpace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiClient.delete<{ message: string }>(`/api/spaces/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: spacesKey })
    },
  })
}

export function useComputeRoutes(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    // Fire-and-forget on the backend: this resolves as soon as the job
    // *starts* (202), not when it finishes — actual completion arrives via
    // the route-progress WebSocket (useRouteProgress below), which is what
    // invalidates spaceRoutesKey/spacesKey once the job reports 'done'.
    mutationFn: () =>
      apiClient.post<{ message: string; progress: RouteProgress }>(`/api/spaces/${id}/route`),
    onSuccess: (res) => {
      queryClient.setQueryData(spaceRouteProgressKey(id), res.progress)
    },
  })
}

export function useRouteProgress(id: number) {
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: spaceRouteProgressKey(id),
    queryFn: async () => {
      const res = await apiClient.get<{ progress: RouteProgress }>(
        `/api/spaces/${id}/route-progress`
      )
      return res.progress
    },
  })

  useEffect(() => {
    const socket = io(API_URL, { withCredentials: true })

    const subscribe = () => socket.emit("subscribe:route-progress", id)
    socket.on("connect", subscribe)

    socket.on("route-progress", (progress: RouteProgress) => {
      queryClient.setQueryData(spaceRouteProgressKey(id), progress)

      if (progress.status === "done") {
        queryClient.invalidateQueries({ queryKey: spaceRoutesKey(id) })
        queryClient.invalidateQueries({ queryKey: spacesKey })
      }
    })

    return () => {
      socket.emit("unsubscribe:route-progress", id)
      socket.disconnect()
    }
  }, [id, queryClient])

  return query
}

export interface SpaceWithStatus extends Space {
  status: SpaceStatus | null
  poiCount: number
  score: SpaceScore
}

export function useSpacesWithStatus() {
  const spacesQuery = useSpaces()
  const spaces = spacesQuery.data ?? []

  const poiRouteQueries = useQueries({
    queries: spaces.flatMap((space) => [
      {
        queryKey: spacePoisKey(space.id),
        queryFn: async () => {
          const res = await apiClient.get<{ pois: Poi[] }>(`/api/spaces/${space.id}/pois`)
          return res.pois
        },
      },
      {
        queryKey: spaceRoutesKey(space.id),
        queryFn: async () => {
          const res = await apiClient.get<{ routes: SpaceRoute[] }>(
            `/api/spaces/${space.id}/routes`
          )
          return res.routes
        },
      },
    ]),
  })

  const items: SpaceWithStatus[] = spaces.map((space, index) => {
    const poisResult = poiRouteQueries[index * 2]
    const routesResult = poiRouteQueries[index * 2 + 1]
    const pois = (poisResult?.data ?? []) as Poi[]
    const routes = (routesResult?.data ?? []) as SpaceRoute[]
    const isLoading = poisResult?.isPending || routesResult?.isPending

    return {
      ...space,
      status: isLoading ? null : deriveStatus(space, pois, routes),
      poiCount: pois.length,
      score: computeScore(pois.length, routes),
    }
  })

  return {
    items,
    isLoading: spacesQuery.isPending,
    isError: spacesQuery.isError,
    error: spacesQuery.error,
    refetch: spacesQuery.refetch,
  }
}
