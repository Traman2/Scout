import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return "—"
  if (seconds === 0) return "0m"

  const minutes = Math.round(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60

  return hours > 0 ? `${hours}h ${remainingMinutes}m` : `${minutes}m`
}

export function getInitials(name: string | undefined): string {
  if (!name) return "?"
  const parts = name.trim().split(/\s+/)
  const initials = parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts[parts.length - 1][0]
  return initials.toUpperCase()
}
