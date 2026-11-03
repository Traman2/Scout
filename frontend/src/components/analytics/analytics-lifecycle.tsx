import { useEffect, useRef } from "react"
import { useLocation } from "react-router-dom"

import type { AuthUser } from "@/lib/auth"
import {
  capturePageView,
  identifyAnalyticsUser,
  resetAnalyticsUser,
} from "@/lib/analytics"

function routeTemplate(pathname: string): string | null {
  if (/^\/spaces\/[^/]+$/.test(pathname)) return "/spaces/:spaceId"

  if (
    pathname === "/sign-in" ||
    pathname === "/sign-up" ||
    pathname === "/spaces" ||
    pathname === "/university-distance"
  ) {
    return pathname
  }

  return null
}

export function AnalyticsPageViews() {
  const location = useLocation()
  const lastNavigationKey = useRef<string | null>(null)

  useEffect(() => {
    if (lastNavigationKey.current === location.key) return

    const template = routeTemplate(location.pathname)
    if (template) capturePageView(template)
    lastNavigationKey.current = location.key
  }, [location.key, location.pathname])

  return null
}

interface AnalyticsIdentityProps {
  user: AuthUser | undefined
  sessionFailed: boolean
}

export function AnalyticsIdentity({ user, sessionFailed }: AnalyticsIdentityProps) {
  useEffect(() => {
    if (sessionFailed) {
      resetAnalyticsUser()
    } else if (user) {
      identifyAnalyticsUser(user)
    }
  }, [sessionFailed, user])

  return null
}
