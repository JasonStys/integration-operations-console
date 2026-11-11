/**
 * File: DesktopControls.test.tsx
 * Purpose: Verify first-launch help and genuine graceful-close acknowledgements/refusals.
 * State: synthetic responses, never an existing local app; locations: docs/code-index.md.
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

import { DesktopControls } from './DesktopControls';

const state = vi.hoisted(() => ({ native: true }));
vi.mock('./desktop', () => ({
  isDesktopSession: () => state.native,
  desktopHeaders: () => ({ 'X-IOC-Session': 'synthetic-only' }),
}));
beforeEach(() => {
  state.native = true;
});
afterEach(() => vi.unstubAllGlobals());

it('leaves ordinary browser deployments unchanged', () => {
  state.native = false;
  const fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  render(<DesktopControls />);
  expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
});

it('explains durable local storage, synthetic identities and confirmed shutdown', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ version: '1.1.0', persistence: 'durable-local-file' })),
    )
    .mockResolvedValueOnce(new Response(JSON.stringify({ stopping: true }), { status: 202 }));
  vi.stubGlobal('fetch', fetchMock);
  render(<DesktopControls />);
  expect(await screen.findByText('Local application 1.1.0')).toBeVisible();
  expect(screen.getByText(/All providers and roles are synthetic/)).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: 'Quit application' }));
  expect(await screen.findByText(/Application is closing safely/)).toBeVisible();
  expect(screen.getByRole('button', { name: 'Quit application' })).toBeDisabled();
  expect(fetchMock).toHaveBeenLastCalledWith(
    '/desktop/quit',
    expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'X-IOC-Session': 'synthetic-only' }),
    }),
  );
});

it.each([403, 200])(
  'keeps a usable native-window fallback for refused or malformed acknowledgements (%s)',
  async (status) => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({ version: '1.1.0', persistence: 'custom-database-configuration' }),
          ),
        )
        .mockResolvedValueOnce(new Response(JSON.stringify({ stopping: false }), { status })),
    );
    render(<DesktopControls />);
    await screen.findByText('Local application 1.1.0');
    await userEvent.click(screen.getByRole('button', { name: 'Quit application' }));
    expect(await screen.findByText(/Close was not confirmed/)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Quit application' })).toBeEnabled();
  },
);

it('reports an expired native session without exposing raw server data', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response('private internal error', { status: 403 })),
  );
  render(<DesktopControls />);
  await waitFor(() =>
    expect(screen.getByRole('status')).toHaveTextContent(
      'Reopen the console from its native window.',
    ),
  );
  expect(screen.queryByText('private internal error')).not.toBeInTheDocument();
});
