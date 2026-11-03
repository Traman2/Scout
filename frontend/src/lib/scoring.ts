import type { SpaceRoute } from "./spaces"

export interface SpaceScore {
  score: number | null
  poiCountScore: number
  groceryScore: number
  gymScore: number
  transitScore: number
  nearestGroceryWalkSeconds: number | null
  nearestGymWalkSeconds: number | null
  nearestTransitWalkSeconds: number | null
}

// Fixed for now, not user-configurable — same "v1: hardcoded" approach the
// backend takes for POI categories (overpass.client.ts CATEGORY_FILTERS) and
// OTP mode buckets (otp.client.ts MODE_BUCKETS). Transit gets the largest
// share since walkability scores generally weight transit access heaviest;
// grocery next since it's the most frequent recurring trip.
export const SCORE_WEIGHTS = {
  poiCount: 0.2,
  grocery: 0.25,
  gym: 0.2,
  transit: 0.35,
} as const

// More discovered POIs is better, but with diminishing returns — a space
// with 60+ nearby POIs gets full credit here; going from 5 to 10 matters far
// more than going from 200 to 205.
const POI_COUNT_SATURATION = 60

// Beyond a 20-minute walk, "nearby" stops being a meaningful selling point
// for this score.
const MAX_WALK_MINUTES = 20

function proximityScore(walkSeconds: number | null): number {
  if (walkSeconds == null) return 0
  const minutes = walkSeconds / 60
  return Math.max(0, Math.min(100, Math.round(100 - (minutes / MAX_WALK_MINUTES) * 100)))
}

function nearestWalkSeconds(routes: SpaceRoute[], category: string): number | null {
  const matches = routes.filter((route) => route.poiCategory === category)
  if (matches.length === 0) return null
  return Math.min(...matches.map((route) => route.walkDurationSeconds))
}

// Pure by design — trivially reusable anywhere the raw poi count + routes
// are available, which today is both the spaces table and the space detail
// page; both already fetch this data for other reasons (status
// derivation, the routes table).
export function computeScore(poiCount: number, routes: SpaceRoute[]): SpaceScore {
  const nearestGroceryWalkSeconds = nearestWalkSeconds(routes, "grocery")
  const nearestGymWalkSeconds = nearestWalkSeconds(routes, "gym")
  const nearestTransitWalkSeconds = nearestWalkSeconds(routes, "transit")

  const poiCountScore = Math.min(100, Math.round((poiCount / POI_COUNT_SATURATION) * 100))
  const groceryScore = proximityScore(nearestGroceryWalkSeconds)
  const gymScore = proximityScore(nearestGymWalkSeconds)
  const transitScore = proximityScore(nearestTransitWalkSeconds)

  // Scoring before routes exist would penalize "not evaluated yet" as if it
  // were "bad" — show no score at all until routes are computed, mirroring
  // deriveStatus's own routes_computed gate.
  const score =
    routes.length === 0
      ? null
      : Math.round(
          poiCountScore * SCORE_WEIGHTS.poiCount +
            groceryScore * SCORE_WEIGHTS.grocery +
            gymScore * SCORE_WEIGHTS.gym +
            transitScore * SCORE_WEIGHTS.transit
        )

  return {
    score,
    poiCountScore,
    groceryScore,
    gymScore,
    transitScore,
    nearestGroceryWalkSeconds,
    nearestGymWalkSeconds,
    nearestTransitWalkSeconds,
  }
}
