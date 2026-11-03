import axios, { type AxiosError } from "axios"

export const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:3000"

export class ApiError extends Error {
  status: number
  error: string
  details?: unknown

  constructor(status: number, error: string, message: string, details?: unknown) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.error = error
    this.details = details
  }
}

interface ApiErrorBody {
  error?: string
  message?: string
  details?: unknown
}

const http = axios.create({
  baseURL: API_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    if (error.response) {
      const { status, data, statusText } = error.response
      return Promise.reject(
        new ApiError(
          status,
          data?.error ?? "Request failed",
          data?.message ?? data?.error ?? statusText,
          data?.details
        )
      )
    }
    return Promise.reject(new ApiError(0, "Network Error", error.message))
  }
)

export const apiClient = {
  get: <T>(path: string) => http.get<T>(path).then((res) => res.data),
  post: <T>(path: string, data?: unknown) => http.post<T>(path, data).then((res) => res.data),
  patch: <T>(path: string, data?: unknown) => http.patch<T>(path, data).then((res) => res.data),
  delete: <T>(path: string) => http.delete<T>(path).then((res) => res.data),
}
