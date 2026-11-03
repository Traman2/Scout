import { zodResolver } from "@hookform/resolvers/zod"
import { PlusIcon } from "lucide-react"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { z } from "zod"

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
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ApiError } from "@/lib/api-client"
import { useCreateSpace } from "@/lib/spaces"

const newSpaceSchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(255),
  address: z.string().trim().min(1, "Address is required").max(1000),
})

type NewSpaceValues = z.infer<typeof newSpaceSchema>

interface NewSpaceDialogProps {
  trigger?: React.ReactElement
}

export function NewSpaceDialog({ trigger }: NewSpaceDialogProps) {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const createSpace = useCreateSpace()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<NewSpaceValues>({ resolver: zodResolver(newSpaceSchema) })

  const onSubmit = handleSubmit((values) => {
    createSpace.mutate(values, {
      onSuccess: (res) => {
        setOpen(false)
        reset()
        navigate(`/spaces/${res.space.id}`)
      },
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Something went wrong. Please try again."
        )
      },
    })
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (!next) reset()
      }}
    >
      <DialogTrigger render={trigger ?? <Button />}>
        {trigger ? undefined : <><PlusIcon />New Space</>}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New Space</DialogTitle>
          <DialogDescription>
            Add a space by name and address, we&apos;ll geocode it automatically.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="space-name">Name</Label>
            <Input id="space-name" {...register("name")} />
            {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="space-address">Address</Label>
            <Input id="space-address" {...register("address")} />
            {errors.address && (
              <p className="text-sm text-destructive">{errors.address.message}</p>
            )}
          </div>
          <DialogFooter>
            <Button type="submit" disabled={createSpace.isPending}>
              {createSpace.isPending ? "Creating…" : "Create Space"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
