import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

interface ScoreBadgeProps {
  score: number | null
  className?: string
}

// Matches the score-tier thresholds used to split the spaces list/board
// (space-groups.ts) — 80+, 60-79, below 60.
function scoreClassName(score: number): string {
  if (score >= 80) return "bg-success text-success-foreground"
  if (score >= 60) return "bg-warning text-warning-foreground"
  return "bg-destructive/10 text-destructive"
}

export function ScoreBadge({ score, className }: ScoreBadgeProps) {
  if (score == null) {
    return (
      <Badge variant="outline" className={cn("border-transparent bg-muted text-muted-foreground", className)}>
        —
      </Badge>
    )
  }

  return (
    <Badge variant="outline" className={cn("border-transparent", scoreClassName(score), className)}>
      {score}
    </Badge>
  )
}
