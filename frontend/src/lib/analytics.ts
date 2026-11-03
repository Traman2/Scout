import posthog from "posthog-js"

import type { AuthUser } from "./auth"

type AnalyticsProperty = string | number | boolean | null
type AnalyticsProperties = Record<string, AnalyticsProperty>

interface AnalyticsEvents {
  $pageview: {
    $current_url: string
    $pathname: string
  }
  university_comparison_viewed: {
    eligible_space_count: number
    commute_count: number
  }
}

const ANALYTICS_PROPERTY_KEYS = {
  $pageview: ["$current_url", "$pathname"],
  university_comparison_viewed: ["eligible_space_count", "commute_count"],
} as const satisfies {
  [Event in keyof AnalyticsEvents]: readonly (keyof AnalyticsEvents[Event])[]
}

const enabled = import.meta.env.VITE_POSTHOG_ENABLED === "true"
const projectToken = import.meta.env.VITE_POSTHOG_PROJECT_TOKEN?.trim()
const host = import.meta.env.VITE_POSTHOG_HOST?.trim()

if (enabled && (!projectToken || !host)) {
  throw new Error(
    "VITE_POSTHOG_PROJECT_TOKEN and VITE_POSTHOG_HOST are required when PostHog is enabled"
  )
}

let initialized = false
let identifiedUserKey: string | null = null

export function initializeAnalytics(): boolean {
  if (!enabled || !projectToken || !host) return false
  if (initialized) return true

  posthog.init(projectToken, {
    api_host: host,
    defaults: "2026-05-30",
    capture_exceptions: {
      capture_unhandled_errors: true,
      capture_unhandled_rejections: true,
      capture_console_errors: false,
    },
    autocapture: false,
    capture_pageview: false,
    capture_pageleave: false,
    disable_session_recording: true,
    internal_or_test_user_hostname: null,
    person_profiles: "identified_only",
  })
  posthog.register({ environment: import.meta.env.MODE })

  initialized = true
  return true
}

export function getAnalyticsClient() {
  return posthog
}

export function captureAnalyticsEvent<Event extends keyof AnalyticsEvents>(
  event: Event,
  properties: AnalyticsEvents[Event]
) {
  if (!initialized) return
  posthog.capture(event, filterAnalyticsProperties(event, properties))
}

export function filterAnalyticsProperties<Event extends keyof AnalyticsEvents>(
  event: Event,
  properties: AnalyticsEvents[Event]
): AnalyticsProperties {
  const filtered: AnalyticsProperties = {}
  const candidateProperties = properties as Record<string, unknown>
  for (const key of ANALYTICS_PROPERTY_KEYS[event]) {
    const value = candidateProperties[String(key)]
    if (
      value === null ||
      typeof value === "string" ||
      typeof value === "number" ||
      typeof value === "boolean"
    ) {
      filtered[String(key)] = value
    }
  }
  return filtered
}

export function capturePageView(routeTemplate: string) {
  if (!initialized) return

  captureAnalyticsEvent("$pageview", {
    $current_url: `${window.location.origin}${routeTemplate}`,
    $pathname: routeTemplate,
  })
}

export function identifyAnalyticsUser(
  user: Pick<AuthUser, "analyticsId" | "role">
) {
  if (!initialized) return
  if (!user.analyticsId) return

  const userKey = `${user.analyticsId}:${user.role}`
  if (identifiedUserKey === userKey) return

  posthog.identify(user.analyticsId, { role: user.role })
  identifiedUserKey = userKey
}

export function resetAnalyticsUser() {
  if (!initialized) return

  posthog.reset()
  identifiedUserKey = null
}
