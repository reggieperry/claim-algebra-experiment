import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Headless build machine (brief §6): bind to 0.0.0.0 so the UI is reachable from the laptop over
// the LAN or an `ssh -L` tunnel. The browser is a pure viewer of the event log the Scala backend
// emits; nothing critical lives in browser storage.
//
// The dev server proxies the backend seams so the browser only ever talks to the frontend origin (no
// CORS, no hardcoded backend host) and Vite forwards to the http4s server on :8080:
//   /events → SSE. http-proxy pipes the upstream response chunk-by-chunk, so the `text/event-stream`
//             streams through UN-buffered; Vite's dev server applies no compression that would batch it.
//   /answer → the human oracle's POST.  /start → relaunch a game.  /rewind → flip a poisoned answer.
const BACKEND = 'http://localhost:8080';
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/events': { target: BACKEND, changeOrigin: true },
      '/answer': { target: BACKEND, changeOrigin: true },
      '/challenge': { target: BACKEND, changeOrigin: true },
      '/start': { target: BACKEND, changeOrigin: true },
      '/reset': { target: BACKEND, changeOrigin: true },
      '/rewind': { target: BACKEND, changeOrigin: true },
    },
  },
  preview: { host: true, port: 4173 },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reportsDirectory: './coverage',
    },
  },
});
