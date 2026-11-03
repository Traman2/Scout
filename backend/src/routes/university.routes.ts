import {
  computeUniversityCommutes,
  getUniversityDashboard,
  saveUniversity,
} from '#controllers/university.controller.ts';
import { requireAuth } from '#middleware/auth.middleware.ts';
import { Router } from 'express';

const router = Router();

router.use(requireAuth);
router.get('/', getUniversityDashboard);
router.post('/', saveUniversity);
router.post('/commutes', computeUniversityCommutes);

export default router;
