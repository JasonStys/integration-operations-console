/**
 * File: vite.config.ts
 * Purpose: React build plus same-origin development proxy to the Java API.
 * Symbols/variables: default Vite configuration; exact lines are in docs/code-index.md.
 */
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  build: {
    sourcemap: true,
  },
});
