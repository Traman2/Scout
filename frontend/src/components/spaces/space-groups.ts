import type { SpaceStatus, SpaceWithStatus } from "@/lib/spaces"
import { STATUS_META } from "./status-badge"

export interface SpaceGroup {
  key: string
  title: string
  items: SpaceWithStatus[]
}

// Every status before "routes_computed" is still one group each — score
// only exists once routes are computed, so splitting by score only makes
// sense for that final stage.
const PRE_SCORE_STATUSES: SpaceStatus[] = ["not_geocoded", "geocoded", "pois_discovered"]

const SCORE_TIERS: { key: string; title: string; test: (score: number) => boolean }[] = [
  { key: "score-high", title: "Score 80+", test: (score) => score >= 80 },
  { key: "score-mid", title: "Score 60-79", test: (score) => score >= 60 && score < 80 },
  { key: "score-low", title: "Score < 60", test: (score) => score < 60 },
]

// Splits the "Routes Computed" stage into three score-tier groups (80+,
// 60–79, <60) instead of one flat group — every space here has a non-null
// score by construction (computeScore only returns null when routes.length
// is 0, which is exactly what excludes a space from "routes_computed").
//
// Empty groups are dropped so the table only shows groups that actually have
// spaces in them.
export function groupSpaces(spaces: SpaceWithStatus[]): SpaceGroup[] {
  const groups: SpaceGroup[] = PRE_SCORE_STATUSES.map((status) => ({
    key: status,
    title: STATUS_META[status].label,
    items: spaces.filter((space) => space.status === status),
  }))

  const computed = spaces.filter((space) => space.status === "routes_computed")

  for (const tier of SCORE_TIERS) {
    groups.push({
      key: tier.key,
      title: tier.title,
      items: computed.filter((space) => tier.test(space.score.score ?? 0)),
    })
  }

  return groups.filter((group) => group.items.length > 0)
}
