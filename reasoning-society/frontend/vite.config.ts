import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Headless build machine (brief §6): bind to 0.0.0.0 so the UI is reachable from the laptop over
// the LAN or an `ssh -L` tunnel. The browser is a pure viewer of the event log the Scala backend
// emits; nothing critical lives in browser storage.
export default defineConfig({
  plugins: [react()],
  server: { host: true, port: 5173 },
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
