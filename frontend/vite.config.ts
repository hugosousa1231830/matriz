/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// The SPA calls the API on the same origin (/api); in development Vite proxies
// those requests to the backend, so no CORS configuration is needed (ADR-0016).
// The backend port is read from the repository's .env (MATRIZ_API_PORT).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', 'MATRIZ_')
  const apiPort = env.MATRIZ_API_PORT || '8080'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target: `http://localhost:${apiPort}`, changeOrigin: false },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      // Node's fetch needs absolute URLs; the browser build keeps the relative /api/v1.
      env: { VITE_API_BASE_URL: 'http://localhost/api/v1' },
    },
  }
})
