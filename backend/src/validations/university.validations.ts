import { z } from 'zod';

export const upsertUniversitySchema = z.object({
  name: z.string().min(1).max(255).trim(),
  address: z.string().min(1).max(1000).trim(),
});
