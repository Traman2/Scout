import {
  createSpace,
  listSpaces,
  getSpace,
  updateSpace,
  deleteSpace,
  geocodeSpace,
} from '#controllers/space.controller.ts';
import { discoverPois, listPois } from '#controllers/space-poi.controller.ts';
import {
  computeRoutes,
  listRoutes,
  getRouteProgressHandler,
} from '#controllers/space-route.controller.ts';
import { requireAuth } from '#middleware/auth.middleware.ts';
import { Router } from 'express';

const router = Router();

router.use(requireAuth);

router.post('/', createSpace);
router.get('/', listSpaces);
router.get('/:id', getSpace);
router.patch('/:id', updateSpace);
router.post('/:id/geocode', geocodeSpace);
router.post('/:id/discover', discoverPois);
router.get('/:id/pois', listPois);
router.post('/:id/route', computeRoutes);
router.get('/:id/route-progress', getRouteProgressHandler);
router.get('/:id/routes', listRoutes);
router.delete('/:id', deleteSpace);

export default router;
