import { ChevronRightIcon } from "lucide-react"
import { useState } from "react"
import { useNavigate } from "react-router-dom"

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { SpaceWithStatus } from "@/lib/spaces"
import { groupSpaces } from "./space-groups"
import { ScoreBreakdownPopover } from "./score-breakdown-popover"
import { StatusBadge } from "./status-badge"

interface SpacesTableViewProps {
  spaces: SpaceWithStatus[]
}

export function SpacesTableView({ spaces }: SpacesTableViewProps) {
  const navigate = useNavigate()

  const pending = spaces.filter((space) => space.status === null)
  const groups = groupSpaces(spaces)

  return (
    <div className="flex flex-col gap-4">
      {pending.length > 0 && (
        <StatusSection title="Loading…" count={pending.length}>
          {pending.map((space) => (
            <TableRow key={space.id}>
              <TableCell colSpan={6}>
                <Skeleton className="h-5 w-full" />
              </TableCell>
            </TableRow>
          ))}
        </StatusSection>
      )}

      {groups.map(({ key, title, items }) => (
        <StatusSection key={key} title={title} count={items.length}>
          {items.map((space) => (
            <TableRow
              key={space.id}
              className="cursor-pointer"
              onClick={() => navigate(`/spaces/${space.id}`)}
            >
              <TableCell className="font-medium whitespace-normal">{space.name}</TableCell>
              <TableCell className="max-w-xs truncate text-muted-foreground">
                {space.address}
              </TableCell>
              <TableCell>
                <StatusBadge status={space.status!} />
              </TableCell>
              <TableCell>
                <ScoreBreakdownPopover score={space.score} poiCount={space.poiCount} />
              </TableCell>
              <TableCell className="text-muted-foreground">{space.poiCount}</TableCell>
              <TableCell className="text-muted-foreground">
                {new Date(space.created_at).toLocaleDateString()}
              </TableCell>
            </TableRow>
          ))}
        </StatusSection>
      ))}
    </div>
  )
}

interface StatusSectionProps {
  title: string
  count: number
  children: React.ReactNode
}

function StatusSection({ title, count, children }: StatusSectionProps) {
  const [open, setOpen] = useState(true)

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="flex w-full items-center gap-2 rounded-lg py-2 text-left">
        <ChevronRightIcon
          className={`size-4 text-muted-foreground transition-transform ${open ? "rotate-90" : ""}`}
        />
        <span className="text-sm font-medium">{title}</span>
        <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
          {count}
        </span>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Address</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Score</TableHead>
              <TableHead>POIs</TableHead>
              <TableHead>Created</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>{children}</TableBody>
        </Table>
      </CollapsibleContent>
    </Collapsible>
  )
}
