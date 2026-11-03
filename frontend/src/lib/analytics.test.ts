import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

const posthogMock = vi.hoisted(() => ({
  capture: vi.fn(),
  identify: vi.fn(),
  init: vi.fn(),
  register: vi.fn(),
  reset: vi.fn(),
}))

vi.mock("posthog-js", () => ({ default: posthogMock }))

async function loadAnalytics(enabled: boolean) {
  vi.stubEnv("VITE_POSTHOG_ENABLED", String(enabled))
  vi.stubEnv("VITE_POSTHOG_PROJECT_TOKEN", "phc_test")
  vi.stubEnv("VITE_POSTHOG_HOST", "https://us.i.posthog.com")
  vi.resetModules()
  return import("./analytics")
}

describe("frontend analytics wrapper", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it("is a no-op when analytics is disabled", async () => {
    const analytics = await loadAnalytics(false)

    expect(analytics.initializeAnalytics()).toBe(false)
    analytics.captureAnalyticsEvent("university_comparison_viewed", {
      eligible_space_count: 2,
      commute_count: 1,
    })
    analytics.identifyAnalyticsUser({
      analyticsId: "scout_masked",
      role: "user",
    })

    expect(posthogMock.init).not.toHaveBeenCalled()
    expect(posthogMock.capture).not.toHaveBeenCalled()
    expect(posthogMock.identify).not.toHaveBeenCalled()
  })

  it("uses only the masked identity and resets it on sign-out", async () => {
    const analytics = await loadAnalytics(true)
    analytics.initializeAnalytics()

    const user = { analyticsId: "scout_masked", role: "user" }
    analytics.identifyAnalyticsUser(user)
    analytics.identifyAnalyticsUser(user)

    expect(posthogMock.identify).toHaveBeenCalledTimes(1)
    expect(posthogMock.identify).toHaveBeenCalledWith("scout_masked", {
      role: "user",
    })

    analytics.resetAnalyticsUser()
    analytics.identifyAnalyticsUser(user)

    expect(posthogMock.reset).toHaveBeenCalledTimes(1)
    expect(posthogMock.identify).toHaveBeenCalledTimes(2)
  })

  it("drops properties outside the event allowlist", async () => {
    const analytics = await loadAnalytics(true)
    analytics.initializeAnalytics()

    const properties = {
      eligible_space_count: 2,
      commute_count: 1,
      email: "should-not-leave-the-browser@example.com",
    }
    analytics.captureAnalyticsEvent("university_comparison_viewed", properties)

    expect(posthogMock.capture).toHaveBeenCalledWith(
      "university_comparison_viewed",
      { eligible_space_count: 2, commute_count: 1 }
    )
  })

  it("configures raw development error capture and keeps localhost events visible", async () => {
    const analytics = await loadAnalytics(true)
    analytics.initializeAnalytics()

    const options = posthogMock.init.mock.calls[0]?.[1]
    expect(options).toEqual(expect.objectContaining({
      internal_or_test_user_hostname: null,
      capture_exceptions: {
        capture_unhandled_errors: true,
        capture_unhandled_rejections: true,
        capture_console_errors: false,
      },
    }))
    expect(options).not.toHaveProperty("before_send")
  })
})
