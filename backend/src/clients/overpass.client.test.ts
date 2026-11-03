import axios, { AxiosError } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { findNearbyPois } from './overpass.client.ts';

vi.mock('axios', async importOriginal => {
  const actual = await importOriginal<typeof import('axios')>();
  return { ...actual, default: { ...actual.default, post: vi.fn() } };
});

// Axios post is replaced by vi.fn above and does not use this.
// eslint-disable-next-line @typescript-eslint/unbound-method
const post = vi.mocked(axios.post);
const response = {
  data: {
    elements: [
      { id: 1, lat: 40, lon: -3, tags: { name: 'Shop', shop: 'supermarket' } },
      { id: 2, lat: 40, lon: -3, tags: { shop: 'supermarket' } },
    ],
  },
};

describe('Overpass retries', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    post.mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it('falls back after a timeout and preserves POI filtering', async () => {
    post.mockRejectedValueOnce(new AxiosError('timeout', 'ECONNABORTED'));
    post.mockResolvedValueOnce(response);
    const result = findNearbyPois(40, -3, 1500);
    await vi.runAllTimersAsync();
    await expect(result).resolves.toEqual([
      { osmId: 1, category: 'grocery', name: 'Shop', lat: 40, lng: -3 },
    ]);
    expect(post.mock.calls.map(call => call[0])).toEqual([
      'https://overpass-api.de/api/interpreter',
      'https://overpass.kumi.systems/api/interpreter',
    ]);
    expect(post.mock.calls[0][1]?.toString()).toContain(
      encodeURIComponent('[timeout:15]')
    );
    expect(post.mock.calls[0][2]?.timeout).toBe(20000);
  });

  it('stops after three attempts and propagates the final timeout', async () => {
    const error = new AxiosError('timeout', 'ECONNABORTED');
    post.mockRejectedValue(error);
    const result = expect(findNearbyPois(40, -3, 1500)).rejects.toBe(error);
    await vi.runAllTimersAsync();
    await result;
    expect(post).toHaveBeenCalledTimes(3);
  });

  it.each([429, 503])('retries HTTP %s', async status => {
    post.mockRejectedValueOnce(
      new AxiosError('upstream', 'ERR_BAD_RESPONSE', undefined, undefined, {
        status,
      } as never)
    );
    post.mockResolvedValueOnce(response);
    const result = findNearbyPois(40, -3, 1500);
    await vi.runAllTimersAsync();
    await result;
    expect(post).toHaveBeenCalledTimes(2);
  });

  it('does not retry invalid queries', async () => {
    const error = new AxiosError(
      'bad query',
      'ERR_BAD_REQUEST',
      undefined,
      undefined,
      { status: 400 } as never
    );
    post.mockRejectedValue(error);
    const result = expect(findNearbyPois(40, -3, 1500)).rejects.toBe(error);
    await vi.runAllTimersAsync();
    await result;
    expect(post).toHaveBeenCalledTimes(1);
  });
});
