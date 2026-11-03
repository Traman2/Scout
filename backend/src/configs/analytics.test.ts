import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

type ErrorHandler = (error: Error) => void;

const posthogMock = vi.hoisted(() => ({
  capture: vi.fn(),
  constructorCall: vi.fn(),
  errorHandler: undefined as ErrorHandler | undefined,
  removeErrorListener: vi.fn(),
  shutdown: vi.fn(() => Promise.resolve()),
}));

const loggerMock = vi.hoisted(() => ({
  warn: vi.fn(),
}));

vi.mock('posthog-node', () => ({
  PostHog: class {
    constructor(apiKey: string, options: { host?: string }) {
      posthogMock.constructorCall(apiKey, options);
    }

    capture(payload: unknown) {
      posthogMock.capture(payload);
    }

    on(event: string, handler: ErrorHandler) {
      if (event === 'error') posthogMock.errorHandler = handler;
      return posthogMock.removeErrorListener;
    }

    shutdown() {
      return posthogMock.shutdown();
    }
  },
}));

vi.mock('#configs/logger.ts', () => ({ default: loggerMock }));

async function loadAnalytics(enabled: boolean) {
  vi.stubEnv('POSTHOG_ENABLED', String(enabled));
  vi.stubEnv('POSTHOG_PROJECT_TOKEN', 'phc_test');
  vi.stubEnv('POSTHOG_HOST', 'https://us.i.posthog.com');
  vi.stubEnv('POSTHOG_IDENTITY_SECRET', 'test-identity-secret');
  vi.stubEnv('NODE_ENV', 'development');
  vi.resetModules();
  return import('./analytics.ts');
}

describe('backend analytics wrapper', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    posthogMock.errorHandler = undefined;
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('does not initialize or capture when disabled', async () => {
    const analytics = await loadAnalytics(false);

    analytics.captureAnalyticsEvent(42, 'user_signed_in', { role: 'user' });

    expect(posthogMock.constructorCall).not.toHaveBeenCalled();
    expect(posthogMock.capture).not.toHaveBeenCalled();
  });

  it('uses a stable masked distinct ID and strips unknown properties', async () => {
    const analytics = await loadAnalytics(true);
    const properties = {
      role: 'user',
      email: 'should-not-reach-posthog@example.com',
    };

    analytics.captureAnalyticsEvent(42, 'user_signed_in', properties);

    const distinctId = analytics.getAnalyticsDistinctId(42);
    expect(distinctId).toMatch(/^scout_[a-f0-9]{64}$/);
    expect(distinctId).not.toBe('42');
    expect(posthogMock.capture).toHaveBeenCalledWith({
      distinctId,
      event: 'user_signed_in',
      properties: { role: 'user', environment: 'development' },
    });
  });

  it('logs asynchronous SDK delivery failures without payload data', async () => {
    await loadAnalytics(true);

    posthogMock.errorHandler?.(
      new Error('request body contained private data')
    );

    expect(loggerMock.warn).toHaveBeenCalledWith(
      'PostHog event delivery failed'
    );
    expect(loggerMock.warn).not.toHaveBeenCalledWith(
      expect.stringContaining('private data')
    );
  });

  it('flushes the client and removes its error listener during shutdown', async () => {
    const analytics = await loadAnalytics(true);

    await analytics.shutdownAnalytics();

    expect(posthogMock.shutdown).toHaveBeenCalledTimes(1);
    expect(posthogMock.removeErrorListener).toHaveBeenCalledTimes(1);
  });
});
