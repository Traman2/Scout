import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Link, useLocation, useNavigate } from "react-router-dom"
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
import { useSignIn } from "@/lib/auth"

const signInSchema = z.object({
  email: z.string().trim().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
})

type SignInValues = z.infer<typeof signInSchema>

export function SignInPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? "/"

  const signIn = useSignIn()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignInValues>({ resolver: zodResolver(signInSchema) })

  const onSubmit = handleSubmit((values) => {
    signIn.mutate(values, {
      onSuccess: () => navigate(from, { replace: true }),
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
        <CardTitle>Log In</CardTitle>
        <CardDescription>Enter your credentials to access your Scout account.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
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
              autoComplete="current-password"
              {...register("password")}
            />
            {errors.password && (
              <p className="text-sm text-destructive">{errors.password.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full" disabled={signIn.isPending}>
            {signIn.isPending ? "Logging in…" : "Log In"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{" "}
          <Link to="/sign-up" className="text-primary underline-offset-4 hover:underline">
            Sign Up
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
