import {
  ArrowLeftIcon,
  GraduationCapIcon,
  LogOutIcon,
} from "lucide-react"
import { Outlet, useLocation, useNavigate } from "react-router-dom"
import { toast } from "sonner"

import { Logo } from "@/components/logo"
import { SpaceHeaderActions } from "@/components/spaces/space-header-actions"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { TooltipProvider } from "@/components/ui/tooltip"
import { Card, CardFooter, CardHeader } from "@/components/ui/card"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ApiError } from "@/lib/api-client"
import { useMe, useSignOut } from "@/lib/auth"
import { getInitials } from "@/lib/utils"

export function AppShell() {
  return (
    <TooltipProvider>
      <AppShellContent />
    </TooltipProvider>
  )
}

function AppShellContent() {
  const { data: user } = useMe()
  const signOut = useSignOut()
  const location = useLocation()
  const navigate = useNavigate()
  const isSpacesHome = location.pathname === "/spaces"
  const isSpaceDetail = /^\/spaces\/[^/]+$/.test(location.pathname)

  const handleSignOut = () => {
    signOut.mutate(undefined, {
      onError: (error) => {
        toast.error(
          error instanceof ApiError ? error.message : "Something went wrong. Please try again."
        )
      },
    })
  }

  return (
    <div className="flex h-dvh min-h-0 min-w-0 flex-col overflow-hidden bg-background">
      <header className="flex min-h-[73px] shrink-0 items-start justify-between gap-3 border-b border-border bg-card px-4 py-4 sm:items-center sm:px-6">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2 sm:gap-3">
          <Logo iconClassName="size-7" textClassName="text-sm" />
          {isSpacesHome ? (
            <>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate("/university-distance")}
            >
              <GraduationCapIcon className="size-3.5" />
              University Distance
            </Button>
            </>
          ) : (
            <>
              <Button variant="outline" size="sm" onClick={() => navigate("/spaces")}>
                <ArrowLeftIcon className="size-3.5" />
                Back to Spaces
              </Button>
              {isSpaceDetail && <SpaceHeaderActions />}
            </>
          )}
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <Button
                variant="ghost"
                size="icon-lg"
                aria-label="Open account menu"
                className="rounded-full p-0 hover:bg-accent data-popup-open:bg-accent"
              />
            }
          >
            <Avatar className="shadow-sm ring-2 ring-background">
              <AvatarFallback className="bg-primary text-xs font-semibold text-primary-foreground">
                {getInitials(user?.name)}
              </AvatarFallback>
            </Avatar>
          </DropdownMenuTrigger>

          <DropdownMenuContent
            align="end"
            sideOffset={8}
            className="w-72 overflow-hidden rounded-2xl p-0 shadow-xl"
          >
            <Card className="gap-0 rounded-2xl bg-transparent py-0 ring-0">
              <CardHeader className="grid grid-cols-[auto_minmax(0,1fr)] items-center gap-x-3 gap-y-1 p-4">
                <Avatar size="lg" className="size-11 shadow-sm">
                  <AvatarFallback className="bg-primary text-sm font-semibold text-primary-foreground">
                    {getInitials(user?.name)}
                  </AvatarFallback>
                </Avatar>
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">{user?.name ?? "Account"}</p>
                  <p className="truncate text-xs text-muted-foreground">{user?.email}</p>
                </div>
                {user?.role && (
                  <Badge
                    variant="secondary"
                    className="col-start-2 h-4 px-1.5 text-[0.625rem] font-medium capitalize"
                  >
                    {user.role}
                  </Badge>
                )}
              </CardHeader>
              <CardFooter className="p-2">
                <DropdownMenuItem
                  variant="destructive"
                  className="w-full justify-center py-2 font-medium"
                  onClick={handleSignOut}
                  disabled={signOut.isPending}
                >
                  <LogOutIcon className="size-3.5" />
                  {signOut.isPending ? "Signing out…" : "Sign Out"}
                </DropdownMenuItem>
              </CardFooter>
            </Card>
          </DropdownMenuContent>
        </DropdownMenu>
      </header>

      <main className="min-h-0 flex-1 overflow-y-auto">
        <Outlet />
      </main>
      </div>
  )
}
