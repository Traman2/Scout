import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { resetAnalyticsUser } from "./analytics"
import { apiClient } from "./api-client"

export interface AuthUser {
  id: number
  analyticsId: string | null
  name: string
  email: string
  role: string
}

interface MeResponse {
  user: AuthUser
}

interface AuthResponse {
  message: string
  user: AuthUser
}

export const authMeKey = ["auth", "me"] as const

export function useMe() {
  return useQuery({
    queryKey: authMeKey,
    queryFn: async () => {
      const res = await apiClient.get<MeResponse>("/api/auth/me")
      return res.user
    },
  })
}

export function useSignUp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; email: string; password: string }) =>
      apiClient.post<AuthResponse>("/api/auth/sign-up", data),
    onSuccess: (res) => {
      queryClient.setQueryData<AuthUser>(authMeKey, res.user)
    },
  })
}

export function useSignIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { email: string; password: string }) =>
      apiClient.post<AuthResponse>("/api/auth/sign-in", data),
    onSuccess: (res) => {
      queryClient.setQueryData<AuthUser>(authMeKey, res.user)
    },
  })
}

export function useSignOut() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.post("/api/auth/sign-out"),
    onSuccess: () => {
      resetAnalyticsUser()
      queryClient.setQueryData(authMeKey, null)
      queryClient.clear()
    },
  })
}
