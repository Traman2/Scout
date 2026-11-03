import { zodResolver } from "@hookform/resolvers/zod"
import { Loader2Icon, RefreshCwIcon } from "lucide-react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ApiError } from "@/lib/api-client"
import { useGeocodeSpace, useSetSpaceCoordinates } from "@/lib/spaces"

const coordinatesSchema = z.object({
  lat: z.number().min(-90, "Must be between -90 and 90").max(90, "Must be between -90 and 90"),
  lng: z.number().min(-180, "Must be between -180 and 180").max(180, "Must be between -180 and 180"),
})

type CoordinatesValues = z.infer<typeof coordinatesSchema>

interface SetCoordinatesFormProps {
  spaceId: number
}

export function SetCoordinatesForm({ spaceId }: SetCoordinatesFormProps) {
  const setCoordinates = useSetSpaceCoordinates(spaceId)
  const geocodeSpace = useGeocodeSpace(spaceId)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CoordinatesValues>({ resolver: zodResolver(coordinatesSchema) })

  const onSubmit = handleSubmit((values) => {
    setCoordinates.mutate(values, {
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Something went wrong. Please try again."
        )
      },
    })
  })

  const handleRetryGeocode = () => {
    geocodeSpace.mutate(undefined, {
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Something went wrong. Please try again."
        )
      },
    })
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>Set Coordinates</CardTitle>
        <CardDescription>
          This space doesn&apos;t have a location yet. Retry geocoding its address, or enter
          latitude and longitude manually to show it on the map.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <Button
          type="button"
          variant="outline"
          className="w-full"
          onClick={handleRetryGeocode}
          disabled={geocodeSpace.isPending}
        >
          {geocodeSpace.isPending ? (
            <Loader2Icon className="size-3.5 animate-spin" />
          ) : (
            <RefreshCwIcon className="size-3.5" />
          )}
          Retry Geocoding
        </Button>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="lat">Latitude</Label>
            <Input
              id="lat"
              type="number"
              step="any"
              placeholder="40.4168"
              {...register("lat", { valueAsNumber: true })}
            />
            {errors.lat && <p className="text-sm text-destructive">{errors.lat.message}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="lng">Longitude</Label>
            <Input
              id="lng"
              type="number"
              step="any"
              placeholder="-3.7038"
              {...register("lng", { valueAsNumber: true })}
            />
            {errors.lng && <p className="text-sm text-destructive">{errors.lng.message}</p>}
          </div>

          <Button type="submit" className="w-full" disabled={setCoordinates.isPending}>
            {setCoordinates.isPending ? "Saving…" : "Save Coordinates"}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
