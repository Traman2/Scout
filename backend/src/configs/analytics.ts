import { createHmac } from 'node:crypto';
import { PostHog } from 'posthog-node';
import logger from '#configs/logger.ts';

export type AnalyticsErrorCode =
  'address_not_found' | 'provider_unavailable' | 'timeout' | 'unknown';

export interface AnalyticsEvents {
  user_signed_up: { role: string };
  user_signed_in: { role: string };
  space_created: {
    space_id: number;
    space_ordinal: number | null;
    is_first_space: boolean | null;
  };
  space_geocoded: { space_id: number };
  space_geocode_failed: {
    space_id: number;
    error_code: AnalyticsErrorCode;
  };
  poi_discovery_completed: {
    space_id: number;
    poi_count: number;
    radius_meters: number;
    duration_ms: number;
  };
  poi_discovery_failed: {
    space_id: number;
    error_code: AnalyticsErrorCode;
    duration_ms: number;
  };
  route_computation_started: {
    space_id: number;
    poi_count: number;
    job_started_at: string;
  };
  route_computation_completed: {
    space_id: number;
    poi_count: number;
    route_count: number;
    duration_ms: number;
  };
  route_computation_failed: {
    space_id: number;
    poi_count: number;
    completed_count: number;
    duration_ms: number;
    error_code: AnalyticsErrorCode;
  };
}

type AnalyticsProperty = string | number | boolean | null;
type AnalyticsProperties = Record<string, AnalyticsProperty>;

const ANALYTICS_PROPERTY_KEYS = {
  user_signed_up: ['role'],
  user_signed_in: ['role'],
  space_created: ['space_id', 'space_ordinal', 'is_first_space'],
  space_geocoded: ['space_id'],
  space_geocode_failed: ['space_id', 'error_code'],
  poi_discovery_completed: [
    'space_id',
    'poi_count',
    'radius_meters',
    'duration_ms',
  ],
  poi_discovery_failed: ['space_id', 'error_code', 'duration_ms'],
  route_computation_started: ['space_id', 'poi_count', 'job_started_at'],
  route_computation_completed: [
    'space_id',
    'poi_count',
    'route_count',
    'duration_ms',
  ],
  route_computation_failed: [
    'space_id',
    'poi_count',
    'completed_count',
    'duration_ms',
    'error_code',
  ],
} as const satisfies {
  [Event in keyof AnalyticsEvents]: readonly (keyof AnalyticsEvents[Event])[];
};

const enabled = process.env.POSTHOG_ENABLED === 'true';
const projectToken = process.env.POSTHOG_PROJECT_TOKEN?.trim();
const host = process.env.POSTHOG_HOST?.trim();
const identitySecret =
  process.env.POSTHOG_IDENTITY_SECRET?.trim() || process.env.JWT_SECRET?.trim();

if (enabled && (!projectToken || !host || !identitySecret)) {
  throw new Error(
    'POSTHOG_PROJECT_TOKEN, POSTHOG_HOST, and POSTHOG_IDENTITY_SECRET or JWT_SECRET are required when PostHog is enabled'
  );
}

const analyticsClient =
  enabled && projectToken && host
    ? new PostHog(projectToken, {
        host,
      })
    : null;

const removeAnalyticsErrorListener = analyticsClient?.on('error', () => {
  logger.warn('PostHog event delivery failed');
});

export const getAnalyticsDistinctId = (userId: number): string | null => {
  if (!identitySecret) return null;

  const digest = createHmac('sha256', identitySecret)
    .update(`scout:posthog:user:${userId}`)
    .digest('hex');
  return `scout_${digest}`;
};

export const filterAnalyticsProperties = <Event extends keyof AnalyticsEvents>(
  event: Event,
  properties: AnalyticsEvents[Event]
): AnalyticsProperties => {
  const filtered: AnalyticsProperties = {};
  const candidateProperties = properties as Record<string, unknown>;
  for (const key of ANALYTICS_PROPERTY_KEYS[event]) {
    const value = candidateProperties[String(key)];
    if (
      value === null ||
      typeof value === 'string' ||
      typeof value === 'number' ||
      typeof value === 'boolean'
    ) {
      filtered[String(key)] = value;
    }
  }
  return filtered;
};

export const captureAnalyticsEvent = <Event extends keyof AnalyticsEvents>(
  userId: number,
  event: Event,
  properties: AnalyticsEvents[Event]
): void => {
  if (!analyticsClient) return;

  const distinctId = getAnalyticsDistinctId(userId);
  if (!distinctId) return;

  try {
    analyticsClient.capture({
      distinctId,
      event,
      properties: {
        ...filterAnalyticsProperties(event, properties),
        environment: process.env.NODE_ENV ?? 'development',
      },
    });
  } catch {
    logger.warn('PostHog event capture failed');
  }
};

export const getAnalyticsErrorCode = (error: unknown): AnalyticsErrorCode => {
  if (!(error instanceof Error)) return 'unknown';

  const message = error.message.toLowerCase();

  if (message === 'address not found') return 'address_not_found';
  if (
    message.includes('timeout') ||
    message.includes('timed out') ||
    message.includes('aborted')
  ) {
    return 'timeout';
  }
  if (
    message.includes('fetch failed') ||
    message.includes('network') ||
    message.includes('econn') ||
    message.includes('rate limit') ||
    message.includes('service unavailable') ||
    /\b50[234]\b/.test(message)
  ) {
    return 'provider_unavailable';
  }

  return 'unknown';
};

export const shutdownAnalytics = async (): Promise<void> => {
  try {
    await analyticsClient?.shutdown();
  } finally {
    removeAnalyticsErrorListener?.();
  }
};
