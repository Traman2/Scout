import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { apiClient } from "./api-client"

export interface University {
  id: number
  ownerId: number
  name: string
  address: string
  lat: number
  lng: number
  created_at: string
  updated_at: string
}

export interface UniversityCommute {
  id: number
  spaceId: number
  spaceName: string
  spaceAddress: string
  spaceLat: number | null
  spaceLng: number | null
  durationSeconds: number
  walkDurationSeconds: number
  metroDurationSeconds: number
  busDurationSeconds: number
  tramDurationSeconds: number
  otherDurationSeconds: number
  waitDurationSeconds: number
  routeGeometry: UniversityRouteGeometryLeg[] | null
  computed_at: string
}

export interface UniversityRouteGeometryLeg {
  mode: string
  encodedPolyline: string
}

export interface UniversityDashboard {
  university: University | null
  commutes: UniversityCommute[]
}

export const universityKey = ["university"] as const

export function useUniversityDashboard() {
  return useQuery({
    queryKey: universityKey,
    queryFn: () => apiClient.get<UniversityDashboard>("/api/university"),
  })
}

export function useSaveUniversity() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: { name: string; address: string }) =>
      apiClient.post<{ message: string; university: University }>("/api/university", data),
    onSuccess: ({ university }) => {
      queryClient.setQueryData<UniversityDashboard>(universityKey, {
        university,
        commutes: [],
      })
    },
  })
}

export function useComputeUniversityCommutes() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () =>
      apiClient.post<{ message: string; commutes: UniversityCommute[] }>(
        "/api/university/commutes"
      ),
    onSuccess: ({ commutes }) => {
      queryClient.setQueryData<UniversityDashboard>(universityKey, (current) => ({
        university: current?.university ?? null,
        commutes,
      }))
    },
  })
}
