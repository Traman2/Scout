import { Outlet } from "react-router-dom"

import { Logo } from "@/components/logo"

export function AuthLayout() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-background px-4">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <Logo />
        <Outlet />
      </div>
    </div>
  )
}
