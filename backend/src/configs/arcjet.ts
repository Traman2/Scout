import arcjet, { shield, detectBot, slidingWindow } from '@arcjet/node';

if (!process.env.ARCJET_KEY) {
  throw new Error('ARCJET_KEY environment variable is required');
}

const aj = arcjet({
  key: process.env.ARCJET_KEY,
  rules: [
    // Shield protects your app from common attacks e.g. SQL injection
    shield({ mode: 'LIVE' }),

    // Create a bot detection rule
    detectBot({
      // Blocks in production; only logs in other environments so local
      // testing tools (Postman, curl, etc.) aren't blocked in dev.
      mode: process.env.NODE_ENV === 'production' ? 'LIVE' : 'DRY_RUN',
      // Block all bots except the following
      allow: [
        'CATEGORY:SEARCH_ENGINE', // Google, Bing, etc
        'CATEGORY:PREVIEW', // Link previews e.g. Slack, Discord
      ],
    }),
    slidingWindow({
      mode: 'LIVE',
      interval: '2s',
      max: 5,
    }),
  ],
});

export default aj;
