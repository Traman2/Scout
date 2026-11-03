import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import posthog from '@posthog/rollup-plugin'
import path from "path"

// https://vite.dev/config/
export default ({ mode }: { mode: string }) => {
  process.env = { ...process.env, ...loadEnv(mode, process.cwd(), '') }

  const personalApiKey = process.env.POSTHOG_API_KEY?.trim()
  const projectId = process.env.POSTHOG_PROJECT_ID?.trim()
  const host = process.env.POSTHOG_HOST?.trim()

  return defineConfig({
    plugins: [
      react(),
      tailwindcss(),
      ...(personalApiKey && projectId
        ? [
            posthog({
              personalApiKey,
              projectId,
              host,
              sourcemaps: {
                enabled: true,
                deleteAfterUpload: true,
              },
            }),
          ]
        : []),
    ],
    resolve: {
      alias: {
        "@": path.resolve(import.meta.dirname, "./src"),
      },
    },
  })
}
