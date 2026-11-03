import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom"

import { AnalyticsPageViews } from "@/components/analytics/analytics-lifecycle"
import { AppShell } from "@/components/app-shell"
import { AuthLayout } from "@/components/auth/auth-layout"
import { RequireAuth } from "@/components/auth/require-auth"
import { SignInPage } from "@/pages/sign-in-page"
import { SignUpPage } from "@/pages/sign-up-page"
import { SpaceDetailPage } from "@/pages/space-detail-page"
import { SpacesPage } from "@/pages/spaces-page"
import { UniversityDistancePage } from "@/pages/university-distance-page"

function App() {
  return (
    <BrowserRouter>
      <AnalyticsPageViews />
      <Routes>
        <Route element={<AuthLayout />}>
          <Route path="/sign-in" element={<SignInPage />} />
          <Route path="/sign-up" element={<SignUpPage />} />
        </Route>

        <Route element={<RequireAuth />}>
          <Route element={<AppShell />}>
            <Route path="/spaces" element={<SpacesPage />} />
            <Route path="/spaces/:spaceId" element={<SpaceDetailPage />} />
            <Route path="/university-distance" element={<UniversityDistancePage />} />
          </Route>
        </Route>

        <Route path="/" element={<Navigate to="/spaces" replace />} />
        <Route path="*" element={<Navigate to="/spaces" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
