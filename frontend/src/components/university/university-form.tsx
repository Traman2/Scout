import { zodResolver } from "@hookform/resolvers/zod"
import { Loader2Icon } from "lucide-react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ApiError } from "@/lib/api-client"
import { useSaveUniversity, type University } from "@/lib/university"

const universitySchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(255),
  address: z.string().trim().min(1, "Address is required").max(1000),
})

type UniversityValues = z.infer<typeof universitySchema>

interface UniversityFormProps {
  university?: University | null
  onCancel?: () => void
  onSaved?: () => void
}

export function UniversityForm({ university, onCancel, onSaved }: UniversityFormProps) {
  const saveUniversity = useSaveUniversity()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<UniversityValues>({
    resolver: zodResolver(universitySchema),
    defaultValues: {
      name: university?.name ?? "",
      address: university?.address ?? "",
    },
  })

  const onSubmit = handleSubmit((values) => {
    saveUniversity.mutate(values, {
      onSuccess: () => {
        toast.success("University saved")
        onSaved?.()
      },
      onError: (error) => {
        toast.error(error instanceof ApiError ? error.message : "Could not save the university.")
      },
    })
  })

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="university-name">University name</Label>
        <Input
          id="university-name"
          placeholder="Universidad Complutense de Madrid"
          {...register("name")}
        />
        {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
      </div>
      <div className="space-y-2">
        <Label htmlFor="university-address">University address</Label>
        <Input
          id="university-address"
          placeholder="Av. de Séneca, 2, Madrid"
          {...register("address")}
        />
        {errors.address && <p className="text-sm text-destructive">{errors.address.message}</p>}
      </div>
      <div className="flex justify-end gap-2">
        {onCancel && (
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
        )}
        <Button type="submit" disabled={saveUniversity.isPending}>
          {saveUniversity.isPending && <Loader2Icon className="size-3.5 animate-spin" />}
          {university ? "Save changes" : "Add university"}
        </Button>
      </div>
    </form>
  )
}
