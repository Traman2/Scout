import { z } from 'zod';

export const createSpaceSchema = z.object({
  name: z.string().min(1).max(255).trim(),
  address: z.string().min(1).max(1000).trim(),
});

export const updateSpaceSchema = z
  .object({
    name: z.string().min(1).max(255).trim(),
    address: z.string().min(1).max(1000).trim(),
    lat: z.coerce.number().min(-90).max(90),
    lng: z.coerce.number().min(-180).max(180),
  })
  .partial()
  .refine(data => Object.keys(data).length > 0, {
    message: 'At least one field must be provided',
  });

export const spaceIdParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
});

export const discoverPoisSchema = z.object({
  radiusMeters: z.coerce.number().int().min(100).max(5000).optional(),
});
