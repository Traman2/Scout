import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { Progress } from "@/components/ui/progress"
import { SCORE_WEIGHTS, type SpaceScore } from "@/lib/scoring"
import { formatDuration } from "@/lib/utils"
import { ScoreBadge } from "./score-badge"

interface ScoreBreakdownPopoverProps {
  score: SpaceScore
  poiCount: number
}

interface ScoreMetric {
  label: string
  detail: string
  score: number
  weight: number
}

export function ScoreBreakdownPopover({ score, poiCount }: ScoreBreakdownPopoverProps) {
  if (score.score == null) return <ScoreBadge score={null} />

  const metrics: ScoreMetric[] = [
    {
      label: "Nearby POIs",
      detail: `${poiCount} discovered`,
      score: score.poiCountScore,
      weight: SCORE_WEIGHTS.poiCount,
    },
    {
      label: "Grocery access",
      detail: `${formatDuration(score.nearestGroceryWalkSeconds)} walk`,
      score: score.groceryScore,
      weight: SCORE_WEIGHTS.grocery,
    },
    {
      label: "Gym access",
      detail: `${formatDuration(score.nearestGymWalkSeconds)} walk`,
      score: score.gymScore,
      weight: SCORE_WEIGHTS.gym,
    },
    {
      label: "Transit access",
      detail: `${formatDuration(score.nearestTransitWalkSeconds)} walk`,
      score: score.transitScore,
      weight: SCORE_WEIGHTS.transit,
    },
  ]

  return (
    <Popover>
      <PopoverTrigger
        aria-label={`Show score breakdown for ${score.score}`}
        className="rounded-md outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        onClick={(event) => event.stopPropagation()}
      >
        <ScoreBadge score={score.score} className="cursor-pointer" />
      </PopoverTrigger>
      <PopoverContent
        align="end"
        sideOffset={8}
        className="w-80 p-0"
        onClick={(event) => event.stopPropagation()}
      >
        <Card size="sm" className="gap-0 border-0 py-0 shadow-none ring-0">
          <CardHeader className="border-b py-3">
            <div className="flex items-start justify-between gap-4">
              <div>
                <CardTitle>Score breakdown</CardTitle>
                <CardDescription>Weighted from nearby places and walking time.</CardDescription>
              </div>
              <ScoreBadge score={score.score} className="shrink-0" />
            </div>
          </CardHeader>
          <CardContent className="space-y-4 py-4">
            {metrics.map((metric) => {
              const weightPercent = Math.round(metric.weight * 100)
              const contribution = (metric.score * metric.weight).toFixed(1)

              return (
                <div key={metric.label} className="space-y-1.5">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium">{metric.label}</p>
                      <p className="text-xs text-muted-foreground">{metric.detail}</p>
                    </div>
                    <span className="text-sm font-semibold tabular-nums">{metric.score}/100</span>
                  </div>
                  <Progress value={metric.score} className="h-1.5" />
                  <p className="text-xs text-muted-foreground">
                    {weightPercent}% weight · {contribution} points
                  </p>
                </div>
              )
            })}
          </CardContent>
          <CardFooter className="justify-between py-3 text-xs">
            <span className="text-muted-foreground">Weighted total</span>
            <span className="font-semibold tabular-nums">{score.score}/100</span>
          </CardFooter>
        </Card>
      </PopoverContent>
    </Popover>
  )
}
