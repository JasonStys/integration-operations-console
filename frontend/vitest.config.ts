/**
 * File: vitest.config.ts
 * Purpose: Browser-like unit tests with deterministic coverage thresholds.
 * Symbols/variables: test environment and coverage configuration; exact lines are in docs/code-index.md.
 */
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/api/schema.d.ts', 'src/test/**'],
      thresholds: {
        lines: 75,
        functions: 75,
        statements: 75,
        branches: 65,
      },
    },
  },
});
