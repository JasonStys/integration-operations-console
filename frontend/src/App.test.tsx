/**
 * File: App.test.tsx
 * Purpose: Accessible component and operator-workflow interaction coverage.
 * Symbols: fake client/state fixtures and App behavior tests; exact lines are in docs/code-index.md.
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { App } from './App';
import type {
  AuditEvent,
  ConnectorAccount,
  Dashboard,
  OperationsClient,
  SyncJob,
} from './api/client';

const account: ConnectorAccount = {
  id: '22222222-2222-2222-2222-222222222222',
  connectorId: 'atlas-ads',
  displayName: 'Demo Operations Account',
  externalReference: 'demo-atlas-ads',
  createdAt: '2026-09-17T12:00:00Z',
};

const job: SyncJob = {
  id: '11111111-1111-1111-1111-111111111111',
  accountId: account.id,
  status: 'QUEUED',
  failurePlan: 'RATE_LIMIT_ONCE',
  attempt: 0,
  maxAttempts: 3,
  pageCursor: null,
  availableAt: '2026-09-17T12:00:00Z',
  correlationId: 'component-test',
  idempotencyKey: 'component-test-key',
  lastError: null,
  recordsProcessed: 0,
  createdAt: '2026-09-17T12:00:00Z',
  updatedAt: '2026-09-17T12:00:00Z',
};

const event: AuditEvent = {
  id: 1,
  jobId: job.id,
  eventType: 'JOB_SUBMITTED',
  actor: 'component-test',
  detail: 'Queued with failure plan RATE_LIMIT_ONCE',
  occurredAt: '2026-09-17T12:00:00Z',
};

function fakeClient(initialAccounts: ConnectorAccount[], initialJobs: SyncJob[]): OperationsClient {
  let accounts = [...initialAccounts];
  let jobs = [...initialJobs];
  const dashboard = (): Dashboard => ({
    statusCounts: {
      QUEUED: jobs.filter((item) => item.status === 'QUEUED').length,
      RUNNING: 0,
      RETRY_WAIT: 0,
      SUCCEEDED: 0,
      DEAD_LETTERED: 0,
      CANCELLED: 0,
    },
    recentJobs: jobs,
  });
  return {
    connectors: vi
      .fn()
      .mockResolvedValue([{ id: 'atlas-ads', displayName: 'Atlas Ads (simulated)' }]),
    accounts: vi.fn().mockImplementation(() => Promise.resolve(accounts)),
    dashboard: vi.fn().mockImplementation(() => Promise.resolve(dashboard())),
    createAccount: vi.fn().mockImplementation(() => {
      accounts = [account];
      return Promise.resolve({ resource: account, created: true });
    }),
    submitJob: vi.fn().mockImplementation(() => {
      jobs = [job];
      return Promise.resolve({ resource: job, created: true });
    }),
    runNext: vi.fn().mockResolvedValue({ job, workAvailable: true }),
    runJob: vi.fn().mockResolvedValue({ ...job, status: 'RETRY_WAIT' }),
    cancelJob: vi.fn().mockResolvedValue({ ...job, status: 'CANCELLED' }),
    replayJob: vi.fn().mockResolvedValue({ ...job, status: 'QUEUED' }),
    audit: vi.fn().mockResolvedValue([event]),
  };
}

describe('App', () => {
  it('provides labelled controls and guides the empty-state account-to-job workflow', async () => {
    const user = userEvent.setup();
    const client = fakeClient([], []);
    render(<App client={client} />);

    expect(
      await screen.findByRole('heading', { name: 'Integration Operations Console' }),
    ).toBeVisible();
    expect(
      await screen.findByText('No jobs yet. Link an account and schedule the first sync.'),
    ).toBeVisible();
    expect(screen.getByLabelText('Connector')).toHaveValue('atlas-ads');
    expect(screen.getByRole('button', { name: 'Submit idempotent job' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Link account' }));
    await waitFor(() => expect(client.createAccount).toHaveBeenCalledOnce());
    expect(await screen.findByText('Fake account is linked.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Submit idempotent job' })).toBeEnabled();

    await user.selectOptions(screen.getByLabelText('Failure scenario'), 'RATE_LIMIT_ONCE');
    await user.click(screen.getByRole('button', { name: 'Submit idempotent job' }));
    await waitFor(() => expect(client.submitJob).toHaveBeenCalledOnce());
    expect(await screen.findByText('rate limit once')).toBeVisible();
  });

  it('runs work and reveals append-only audit evidence', async () => {
    const user = userEvent.setup();
    const client = fakeClient([account], [job]);
    render(<App client={client} />);

    await screen.findByText(job.id.slice(0, 8));
    await user.click(screen.getByRole('button', { name: 'Run next eligible job' }));
    await waitFor(() => expect(client.runNext).toHaveBeenCalledOnce());

    await user.click(screen.getByRole('button', { name: 'Audit' }));
    expect(await screen.findByText('JOB SUBMITTED')).toBeVisible();
    expect(screen.getByText(event.detail)).toBeVisible();
  });

  it('reports safe client errors in a polite live region', async () => {
    const client = fakeClient([], []);
    vi.mocked(client.dashboard).mockRejectedValueOnce(new Error('API unavailable'));
    render(<App client={client} />);

    expect(await screen.findByText('Error: API unavailable')).toBeVisible();
  });
});
