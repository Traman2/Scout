import type { FunctionComponent } from "react"
import type { PostHogErrorBoundaryFallbackProps } from "@posthog/react"

export const AnalyticsErrorFallback: FunctionComponent<
  PostHogErrorBoundaryFallbackProps
> = () => (
  <main className="flex min-h-screen items-center justify-center bg-background px-6">
    <div className="max-w-md space-y-4 text-center">
      <h1 className="text-xl font-semibold">Scout hit an unexpected error</h1>
      <p className="text-sm text-muted-foreground">
        The error was recorded without displaying or sending your private account details.
      </p>
      <button
        type="button"
        className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
        onClick={() => window.location.reload()}
      >
        Reload Scout
      </button>
    </div>
  </main>
)
