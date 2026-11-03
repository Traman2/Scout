import { QueryClientProvider } from '@tanstack/react-query'
import { PostHogErrorBoundary, PostHogProvider } from '@posthog/react'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { Toaster } from '@/components/ui/sonner'
import { AnalyticsErrorFallback } from '@/components/analytics/analytics-error-fallback'
import { getAnalyticsClient, initializeAnalytics } from '@/lib/analytics'
import { queryClient } from '@/lib/query-client'
import App from './App.tsx'

import 'leaflet/dist/leaflet.css'
import './index.css'

const app = (
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
      <Toaster />
    </QueryClientProvider>
  </StrictMode>
)

const analyticsEnabled = initializeAnalytics()

createRoot(document.getElementById('root')!).render(
  analyticsEnabled ? (
    <PostHogProvider client={getAnalyticsClient()}>
      <PostHogErrorBoundary fallback={AnalyticsErrorFallback}>
        {app}
      </PostHogErrorBoundary>
    </PostHogProvider>
  ) : (
    app
  )
)
