import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Link, useNavigate } from "react-router-dom"
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
import { useSignUp } from "@/lib/auth"

const signUpSchema = z.object({
  name: z.string().trim().min(2, "Name must be at least 2 characters").max(255),
  email: z.string().trim().email("Enter a valid email address"),
  password: z.string().min(6, "Password must be at least 6 characters").max(128),
})

type SignUpValues = z.infer<typeof signUpSchema>

export function SignUpPage() {
  const navigate = useNavigate()
  const signUp = useSignUp()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignUpValues>({ resolver: zodResolver(signUpSchema) })

  const onSubmit = handleSubmit((values) => {
    signUp.mutate(values, {
      onSuccess: () => navigate("/", { replace: true }),
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Something went wrong. Please try again."
        )
      },
    })
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Sign Up</CardTitle>
        <CardDescription>Enter your details to create your Scout account.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name">Name</Label>
            <Input id="name" autoComplete="name" {...register("name")} />
            {errors.name && (
              <p className="text-sm text-destructive">{errors.name.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" autoComplete="email" {...register("email")} />
            {errors.email && (
              <p className="text-sm text-destructive">{errors.email.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              {...register("password")}
            />
            {errors.password && (
              <p className="text-sm text-destructive">{errors.password.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full" disabled={signUp.isPending}>
            {signUp.isPending ? "Signing up…" : "Sign Up"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link to="/sign-in" className="text-primary underline-offset-4 hover:underline">
            Log In
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
