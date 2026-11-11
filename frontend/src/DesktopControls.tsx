/**
 * File: DesktopControls.tsx
 * Purpose: Explain durable native workspace, synthetic role limits and graceful application exit.
 * State: public version/persistence and close errors only; lines: docs/code-index.md.
 */
import { useEffect, useState } from 'react';

import { desktopHeaders, isDesktopSession } from './desktop';

/** Native-only guidance; ordinary developer/browser deployments keep their existing behavior. */
export function DesktopControls() {
  const [info, setInfo] = useState<{ version: string; persistence: string } | null>(null);
  const [message, setMessage] = useState('');
  const [stopping, setStopping] = useState(false);
  useEffect(() => {
    if (!isDesktopSession()) return;
    const abort = new AbortController();
    void fetch('/desktop/info', { headers: desktopHeaders(), signal: abort.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('Reopen the console from its native window.');
        return (await response.json()) as { version: string; persistence: string };
      })
      .then(setInfo)
      .catch((error: unknown) => {
        if (!abort.signal.aborted)
          setMessage(error instanceof Error ? error.message : 'Local session is unavailable.');
      });
    return () => abort.abort();
  }, []);
  if (!isDesktopSession()) return null;
  async function quit() {
    setStopping(true);
    try {
      const response = await fetch('/desktop/quit', {
        method: 'POST',
        headers: { ...desktopHeaders(), 'Content-Type': 'application/json' },
        body: '{}',
      });
      const result = (await response.json()) as { stopping?: boolean };
      if (!response.ok || result.stopping !== true)
        throw new Error('The app did not accept shutdown.');
      setMessage('Application is closing safely. You can close this browser tab.');
    } catch {
      setMessage('Close was not confirmed. Use Quit in the native application window.');
      setStopping(false);
    }
  }
  return (
    <aside className="panel desktop-help" aria-label="Local application and help">
      <p>
        <strong>Local application {info?.version ?? ''}</strong> ·{' '}
        {info?.persistence === 'durable-local-file'
          ? 'Your local workspace persists between launches.'
          : 'Using the explicitly configured database.'}
      </p>
      <details open>
        <summary>First workflow and help</summary>
        <ol>
          <li>
            Link a fictional provider account. The same reference reuses the existing account.
          </li>
          <li>Schedule a healthy or failing sync, then run it and review its current state.</li>
          <li>Filter job history, inspect retries/audit and download the captured review.</li>
          <li>Quit here or in the native window. Reopen the app to continue the same workspace.</li>
        </ol>
        <p>
          All providers and roles are synthetic demonstrations. This is a local operations lab, not
          a hosted multiuser service. Data stays on this computer unless you explicitly export it.
        </p>
        <p>
          Created by <a href="https://github.com/JasonStys">Jason Stys</a>.{' '}
          <a href="https://www.linkedin.com/in/jason-stys-31b30b351/">Contact and portfolio</a>.
        </p>
      </details>
      <button className="button" type="button" disabled={stopping} onClick={() => void quit()}>
        Quit application
      </button>
      <p role="status">{message}</p>
    </aside>
  );
}
