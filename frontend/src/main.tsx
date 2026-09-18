/**
 * File: main.tsx
 * Purpose: Mounts the React operations console into the document root.
 * Symbols: rootElement and React root; exact lines are generated in docs/code-index.md.
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App';
import './styles.css';

const rootElement = document.getElementById('root');
if (rootElement === null) {
  throw new Error('Application root element is missing');
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
