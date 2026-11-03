import { AxiosError } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Request, Response } from 'express';
import { discoverPois } from './space-poi.controller.ts';
import logger from '#configs/logger.ts';
import { discoverPoisForSpace } from '#services/poi.service.ts';
import { getSpaceForUser } from '#services/space.service.ts';

vi.mock('#configs/logger.ts', () => ({ default: { error: vi.fn() } }));
vi.mock('#configs/analytics.ts', () => ({
  captureAnalyticsEvent: vi.fn(),
  getAnalyticsErrorCode: vi.fn(() => 'timeout'),
}));
vi.mock('#services/poi.service.ts', () => ({
  DEFAULT_POI_RADIUS_METERS: 1500,
  discoverPoisForSpace: vi.fn(),
  listPoisForSpace: vi.fn(),
}));
vi.mock('#services/space.service.ts', () => ({ getSpaceForUser: vi.fn() }));

const req = {
  params: { id: '45' },
  body: {},
  user: { id: 1 },
} as unknown as Request;
const res = {
  status: vi.fn().mockReturnThis(),
  json: vi.fn().mockReturnThis(),
};

describe('discovery error responses', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getSpaceForUser).mockResolvedValue({
      id: 45,
      lat: 40,
      lng: -3,
    } as never);
  });

  it.each(['ECONNABORTED', 'ETIMEDOUT'])(
    'maps %s to retryable 504 and logs once without Axios internals',
    async code => {
      vi.mocked(discoverPoisForSpace).mockRejectedValue(
        new AxiosError('timeout', code)
      );
      await discoverPois(req, res as unknown as Response);
      expect(res.status).toHaveBeenCalledWith(504);
      expect(res.json).toHaveBeenCalledWith({
        error: 'POI discovery timed out',
        retryable: true,
        message:
          'The nearby places service timed out. Please try again shortly.',
      });
      expect(logger.error).toHaveBeenCalledExactlyOnceWith(
        'Discover POIs error',
        { code, message: 'timeout', spaceId: 45 }
      );
    }
  );

  it('keeps unexpected failures at 500 with a sanitized log', async () => {
    vi.mocked(discoverPoisForSpace).mockRejectedValue(
      new Error('database failed')
    );
    await discoverPois(req, res as unknown as Response);
    expect(res.status).toHaveBeenCalledWith(500);
    expect(logger.error).toHaveBeenCalledExactlyOnceWith(
      'Discover POIs error',
      { code: undefined, message: 'database failed', spaceId: 45 }
    );
  });

  it('preserves ownership 404s', async () => {
    vi.mocked(getSpaceForUser).mockRejectedValue(new Error('Space not found'));
    await discoverPois(req, res as unknown as Response);
    expect(res.status).toHaveBeenCalledWith(404);
    expect(logger.error).not.toHaveBeenCalled();
  });
});
