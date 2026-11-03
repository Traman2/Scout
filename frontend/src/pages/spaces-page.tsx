import { MapPinIcon } from "lucide-react"

import { NewSpaceDialog } from "@/components/spaces/new-space-dialog"
import { SpacesTableView } from "@/components/spaces/spaces-table-view"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useSpacesWithStatus } from "@/lib/spaces"

export function SpacesPage() {
  const { items, isLoading, isError, refetch } = useSpacesWithStatus()

  return (
    <div className="mx-auto max-w-[1600px] px-4 py-6 sm:px-8 sm:py-9">
      <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="mb-2 text-[10px] font-semibold tracking-[0.18em] text-muted-foreground uppercase">Apartment List</p>
          <h1 className="font-heading text-3xl font-semibold tracking-tight">Dashboard</h1>
          <p className="mt-2 text-sm text-muted-foreground">Apartments ranked by walkabiliy score</p>
        </div>
        <NewSpaceDialog />
      </div>

      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : isError ? (
        <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-border py-16 text-center">
          <p className="text-sm text-muted-foreground">Couldn&apos;t load your spaces.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center gap-4 rounded-lg border border-dashed border-border py-20 text-center">
          <div className="flex size-12 items-center justify-center rounded-full bg-muted">
            <MapPinIcon className="size-6 text-muted-foreground" />
          </div>
          <div className="flex flex-col gap-1">
            <h2 className="font-heading text-base font-medium">Track your first space</h2>
            <p className="max-w-sm text-sm text-muted-foreground">
              Add a space by address, we&apos;ll geocode it, surface nearby amenities, and
              compute commute times.
            </p>
          </div>
          <NewSpaceDialog trigger={<Button>New Space</Button>} />
        </div>
      ) : (
        <SpacesTableView spaces={items} />
      )}
    </div>
  )
}
