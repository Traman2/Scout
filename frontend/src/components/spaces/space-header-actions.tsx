import { Loader2Icon, RouteIcon, SearchIcon, Trash2Icon } from "lucide-react"
import { useEffect, useRef, useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Progress, ProgressIndicator, ProgressTrack } from "@/components/ui/progress"
import { ApiError } from "@/lib/api-client"
import {
  useComputeRoutes,
  useDeleteSpace,
  useDiscoverPois,
  useRouteProgress,
  useSpace,
  useSpacePois,
  useSpaceRoutes,
} from "@/lib/spaces"

export function SpaceHeaderActions() {
  const { spaceId } = useParams<{ spaceId: string }>()
  const id = Number(spaceId)
  const navigate = useNavigate()
  const [deleteOpen, setDeleteOpen] = useState(false)

  const { data: space } = useSpace(id)
  const { data: pois, isPending: poisPending } = useSpacePois(id)
  const { data: routes, isPending: routesPending } = useSpaceRoutes(id)
  const { data: progress } = useRouteProgress(id)

  const discoverPois = useDiscoverPois(id)
  const computeRoutes = useComputeRoutes(id)
  const deleteSpace = useDeleteSpace()

  // Toast a failed compute-routes job once per occurrence — progress stays
  // in "error" status (and this component can remount showing it) until the
  // next attempt, so dedupe on finishedAt rather than toasting every render.
  const lastErrorToastRef = useRef<string | null>(null)
  useEffect(() => {
    if (
      progress?.status === "error" &&
      progress.finishedAt &&
      progress.finishedAt !== lastErrorToastRef.current
    ) {
      lastErrorToastRef.current = progress.finishedAt
      toast.error(progress.error ?? "Failed to compute routes. Please try again.")
    }
  }, [progress?.status, progress?.finishedAt, progress?.error])

  if (!spaceId || Number.isNaN(id) || !space) {
    return null
  }

  const handleDelete = () => {
    deleteSpace.mutate(id, {
      onSuccess: () => {
        setDeleteOpen(false)
        navigate("/spaces")
      },
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Something went wrong. Please try again."
        )
      },
    })
  }

  const deleteAction = (
    <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
      <DialogTrigger render={<Button variant="outline" size="sm" />}>
        <Trash2Icon className="size-3.5" />
        Delete
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete space</DialogTitle>
          <DialogDescription>
            This will permanently delete &quot;{space.name}&quot; and its discovered POIs and
            routes. This can&apos;t be undone.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => setDeleteOpen(false)}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={deleteSpace.isPending}>
            {deleteSpace.isPending ? (
              <Loader2Icon className="size-3.5 animate-spin" />
            ) : (
              "Delete"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )

  if (space.lat == null || space.lng == null) {
    return deleteAction
  }

  // Wait for both queries to settle before rendering anything, so the
  // buttons don't flash in and then immediately disappear once the real
  // POI/route counts come back. Once settled — success or a permanent
  // fetch error alike — fall back to treating unknown data as "not done
  // yet" so a failed GET doesn't hide these buttons forever; clicking
  // Find POIs/Compute Routes fires an independent POST that can still
  // succeed and will refresh the underlying query on completion.
  if (poisPending || routesPending) return deleteAction

  const hasPois = (pois ?? []).length > 0
  const hasRoutes = (routes ?? []).length > 0
  const routesNeedGeometry =
    hasRoutes && (routes ?? []).some((route) => (route.routeGeometry?.length ?? 0) === 0)

  if (hasPois && hasRoutes && !routesNeedGeometry) return deleteAction

  const handleDiscover = () => {
    discoverPois.mutate(undefined, {
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Something went wrong. Please try again."
        )
      },
    })
  }

  const handleComputeRoutes = () => {
    computeRoutes.mutate(undefined, {
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Something went wrong. Please try again."
        )
      },
    })
  }

  return (
    <>
      {!hasPois && (
        <Button
          variant="outline"
          size="sm"
          onClick={handleDiscover}
          disabled={discoverPois.isPending}
        >
          {discoverPois.isPending ? (
            <Loader2Icon className="size-3.5 animate-spin" />
          ) : (
            <SearchIcon className="size-3.5" />
          )}
          Find POIs
        </Button>
      )}
      {hasPois && (!hasRoutes || routesNeedGeometry) && progress?.status === "in_progress" ? (
        <div className="flex h-8 items-center gap-2 rounded-md border border-input bg-background px-3">
          <RouteIcon className="size-3.5 shrink-0 text-muted-foreground" />
          <Progress
            value={progress.total > 0 ? (progress.completed / progress.total) * 100 : 0}
            className="w-28"
          >
            <ProgressTrack>
              <ProgressIndicator />
            </ProgressTrack>
          </Progress>
          <span className="text-[0.8rem] whitespace-nowrap text-muted-foreground tabular-nums">
            {progress.completed}/{progress.total}
          </span>
        </div>
      ) : (
        hasPois &&
        (!hasRoutes || routesNeedGeometry) && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleComputeRoutes}
            disabled={computeRoutes.isPending}
          >
            {computeRoutes.isPending ? (
              <Loader2Icon className="size-3.5 animate-spin" />
            ) : (
              <RouteIcon className="size-3.5" />
            )}
            {routesNeedGeometry ? "Add Route Lines" : "Compute Routes"}
          </Button>
        )
      )}
      {deleteAction}
    </>
  )
}
