import {
  signup,
  signin,
  signout,
  getCurrentUser,
} from '#controllers/auth.controller.ts';
import { requireAuth } from '#middleware/auth.middleware.ts';
import { Router } from 'express';

const router = Router();

router.post('/sign-up', signup);

router.post('/sign-in', signin);

router.post('/sign-out', signout);

router.get('/me', requireAuth, getCurrentUser);

export default router;
