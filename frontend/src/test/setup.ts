/**
 * File: setup.ts
 * Purpose: Registers DOM matchers and deterministic test cleanup.
 * Symbols/variables: global Vitest setup only; exact lines are in docs/code-index.md.
 */
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => cleanup());
