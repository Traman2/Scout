import express, { type Response } from 'express';
import logger from '#configs/logger.ts';
import morgan from 'morgan';
import cors from 'cors';
import helmet from 'helmet';
import cookieParser from 'cookie-parser';
import authRoutes from '#routes/auth.routes.js';
import spaceRoutes from '#routes/space.routes.ts';
import universityRoutes from '#routes/university.routes.ts';
// import securityMiddleware from '#middleware/security.middleware.ts';
import { identifyUser } from '#middleware/auth.middleware.ts';

if (!process.env.COOKIE_SECRET) {
  throw new Error('COOKIE_SECRET environment variable is required');
}

if (!process.env.CORS_ORIGIN) {
  throw new Error('CORS_ORIGIN environment variable is required');
}
export const allowedOrigins = process.env.CORS_ORIGIN.split(',').map(origin =>
  origin.trim()
);

const app = express();

app.use(helmet());
app.use(cors({ origin: allowedOrigins, credentials: true }));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser(process.env.COOKIE_SECRET));
app.use(
  morgan('combined', {
    stream: {
      write: message => logger.info(message.trim()),
    },
  })
);
app.use(identifyUser);
// app.use(securityMiddleware);
app.use('/api/auth', authRoutes);
app.use('/api/spaces', spaceRoutes);
app.use('/api/university', universityRoutes);

app.get('/', (_req, res: Response) => {
  res.status(200).send('Hello from Scout API');
});

export default app;
