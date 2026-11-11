/**
 * File: desktop.ts
 * Purpose: Bind packaged browser requests to a per-launch local session without cookies or URL retention.
 * State: session exists only in this tab's same-origin storage/memory; exact lines: docs/code-index.md.
 */
let session: string | null = null;
const storageKey = 'ioc-local-launch';

/** Consume only the owned loopback launch fragment; never send it to another origin or log it. */
export function initializeDesktopSession(): void {
  if (typeof window === 'undefined' || window.location.hostname !== '127.0.0.1') return;
  const fragment = /^#ioc=([a-f0-9]{64})$/u.exec(window.location.hash);
  if (fragment) {
    session = fragment[1] ?? null;
    window.history.replaceState(null, '', window.location.pathname + window.location.search);
    try {
      window.sessionStorage.setItem(storageKey, session ?? '');
    } catch {
      // This page remains usable in memory; reopen it from the app after a storage-blocked reload.
    }
  } else {
    try {
      const saved = window.sessionStorage.getItem(storageKey);
      session = saved !== null && /^[a-f0-9]{64}$/u.test(saved) ? saved : null;
    } catch {
      session = null;
    }
  }
}

/** Headers apply only to this page's literal loopback origin, never a configured external API. */
export function desktopHeaders(baseUrl = ''): Record<string, string> {
  if (session === null) return {};
  if (
    window.location.hostname !== '127.0.0.1' ||
    new URL(baseUrl || '/', window.location.href).origin !== window.location.origin
  )
    throw new Error('A local application session cannot be sent to another service.');
  return { 'X-IOC-Session': session };
}

/** @return whether this tab was opened by the native application */
export function isDesktopSession(): boolean {
  return session !== null;
}

initializeDesktopSession();
