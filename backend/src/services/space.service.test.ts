import { beforeEach, describe, expect, it, vi } from 'vitest';

const serviceMock = vi.hoisted(
  (): {
    geocodeAddress: ReturnType<typeof vi.fn>;
    insertedValues: unknown;
    updatedValues: unknown;
  } => ({
    geocodeAddress: vi.fn(),
    insertedValues: undefined,
    updatedValues: undefined,
  })
);

const createdSpace = {
  id: 7,
  ownerId: 42,
  name: 'Test space',
  address: 'Private address',
  lat: null,
  lng: null,
};

vi.mock('#clients/nominatim.client.ts', () => ({
  geocodeAddress: serviceMock.geocodeAddress,
}));

vi.mock('#configs/logger.ts', () => ({
  default: {
    error: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
  },
}));

vi.mock('#configs/database.ts', () => ({
  db: {
    insert: vi.fn(() => ({
      values: (values: unknown) => {
        serviceMock.insertedValues = values;
        return {
          returning: () => Promise.resolve([createdSpace]),
        };
      },
    })),
    update: vi.fn(() => ({
      set: (values: unknown) => {
        serviceMock.updatedValues = values;
        return {
          where: () => ({
            returning: () => Promise.resolve([createdSpace]),
          }),
        };
      },
    })),
  },
}));

describe('automatic space geocoding outcomes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMock.insertedValues = undefined;
    serviceMock.updatedValues = undefined;
  });

  it('returns a failed attempt when create geocoding finds no address', async () => {
    serviceMock.geocodeAddress.mockResolvedValue(null);
    const { createSpace } = await import('./space.service.ts');

    const result = await createSpace({
      ownerId: 42,
      name: 'Test space',
      address: 'Private address',
    });

    expect(result.space).toBe(createdSpace);
    expect(result.geocodeAttempt.status).toBe('failed');
    if (
      result.geocodeAttempt.status !== 'failed' ||
      !(result.geocodeAttempt.error instanceof Error)
    ) {
      throw new Error('Expected a failed geocoding attempt');
    }
    expect(result.geocodeAttempt.error.message).toBe('Address not found');
    expect(serviceMock.insertedValues).toMatchObject({
      lat: undefined,
      lng: undefined,
    });
  });

  it('returns the successful attempt used for persisted coordinates', async () => {
    serviceMock.geocodeAddress.mockResolvedValue({ lat: 40.4, lng: -3.7 });
    const { createSpace } = await import('./space.service.ts');

    const result = await createSpace({
      ownerId: 42,
      name: 'Test space',
      address: 'Private address',
    });

    expect(result.geocodeAttempt).toEqual({
      status: 'succeeded',
      coordinates: { lat: 40.4, lng: -3.7 },
    });
    expect(serviceMock.insertedValues).toMatchObject({
      lat: 40.4,
      lng: -3.7,
    });
  });

  it('reports update geocoding failures without blocking the update', async () => {
    serviceMock.geocodeAddress.mockRejectedValue(new Error('Network timeout'));
    const { updateSpaceForUser } = await import('./space.service.ts');

    const result = await updateSpaceForUser({
      id: 7,
      ownerId: 42,
      data: { address: 'Private address' },
    });

    expect(result.space).toBe(createdSpace);
    expect(result.geocodeAttempt?.status).toBe('failed');
    if (
      result.geocodeAttempt?.status !== 'failed' ||
      !(result.geocodeAttempt.error instanceof Error)
    ) {
      throw new Error('Expected a failed geocoding attempt');
    }
    expect(result.geocodeAttempt.error.message).toBe('Network timeout');
    expect(serviceMock.updatedValues).toMatchObject({
      address: 'Private address',
    });
  });
});
