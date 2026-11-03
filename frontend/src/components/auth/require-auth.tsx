import { Navigate, Outlet, useLocation } from "react-router-dom"

import { AnalyticsIdentity } from "@/components/analytics/analytics-lifecycle"
import { useMe } from "@/lib/auth"

export function RequireAuth() {
  const location = useLocation()
  const { data: user, isPending, isError } = useMe()

  return (
    <>
      <AnalyticsIdentity user={user} sessionFailed={isError} />
      <AuthGuardContent
        userPresent={Boolean(user)}
        isPending={isPending}
        isError={isError}
        redirectFrom={location.pathname}
      />
    </>
  )
}

interface AuthGuardContentProps {
  userPresent: boolean
  isPending: boolean
  isError: boolean
  redirectFrom: string
}

function AuthGuardContent({
  userPresent,
  isPending,
  isError,
  redirectFrom,
}: AuthGuardContentProps) {

  if (isPending) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </div>
    )
  }

  if (isError || !userPresent) {
    return <Navigate to="/sign-in" state={{ from: redirectFrom }} replace />
  }

  return <Outlet />
}
