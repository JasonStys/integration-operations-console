/**
 * File: App.test.tsx
 * Purpose: Accessible component and operator-workflow interaction coverage.
 * Symbols: fake client/state fixtures and App behavior tests; exact lines are in docs/code-index.md.
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import type {
  AuditEvent,
  ConnectorAccount,
  Dashboard,
  OperationsClient,
  SyncJob,
} from './api/client';
import { HttpOperationsClient } from './api/client';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

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
    jobs: vi.fn().mockImplementation(() => Promise.resolve({ items: jobs, nextCursor: null })),
    inspection: vi.fn().mockImplementation((id: string) =>
      Promise.resolve({
        job: jobs.find((item) => item.id === id) ?? job,
        capturedAt: '2026-09-17T12:00:00Z',
        nextEligibleDelayMs: 0,
        history: { items: [event], nextBefore: null },
        notice: 'Captured fixture, not live availability.',
      }),
    ),
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

  it('sends entered provider references and keeps a request key until an explicit new sync', async () => {
    const user = userEvent.setup();
    const client = fakeClient([account], [job]);
    render(<App client={client} />);
    await screen.findByText('Console ready.');
    const reference = screen.getByLabelText('Provider account reference');
    await user.clear(reference);
    await user.type(reference, 'regional-account-02');
    await user.clear(screen.getByLabelText('Display name'));
    await user.type(screen.getByLabelText('Display name'), 'Regional Operations');
    await user.click(screen.getByRole('button', { name: 'Link account' }));
    await waitFor(() =>
      expect(client.createAccount).toHaveBeenCalledWith({
        connectorId: 'atlas-ads',
        displayName: 'Regional Operations',
        externalReference: 'regional-account-02',
      }),
    );
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Submit idempotent job' })).toBeEnabled(),
    );
    const key = screen.getByLabelText('Sync request key');
    await user.clear(key);
    await user.type(key, 'review:request-key-001');
    await user.selectOptions(screen.getByLabelText('Failure scenario'), 'TRANSIENT_TWICE');
    await user.click(screen.getByRole('button', { name: 'Submit idempotent job' }));
    await screen.findByText('Job accepted with an idempotency key.');
    expect(client.submitJob).toHaveBeenLastCalledWith(
      { accountId: account.id, failurePlan: 'TRANSIENT_TWICE', maxAttempts: 3 },
      'review:request-key-001',
    );
    expect(key).toHaveValue('review:request-key-001');
    await user.click(screen.getByRole('button', { name: 'Submit idempotent job' }));
    await waitFor(() => expect(client.submitJob).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Generate new sync request key' })).toBeEnabled(),
    );
    await user.click(screen.getByRole('button', { name: 'Generate new sync request key' }));
    expect(key).not.toHaveValue('review:request-key-001');
    expect((key as HTMLInputElement).value).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('retains the entered key after a rejected request and lets the operator correct the conflict', async () => {
    const user = userEvent.setup();
    const client = fakeClient([account], [job]);
    vi.mocked(client.submitJob).mockRejectedValueOnce(
      new Error('Idempotency key belongs to a different request'),
    );
    render(<App client={client} />);
    await screen.findByText('Console ready.');
    const key = screen.getByLabelText('Sync request key');
    await user.clear(key);
    await user.type(key, 'existing-request-001');
    await user.click(screen.getByRole('button', { name: 'Submit idempotent job' }));
    expect(
      await screen.findByText('Error: Idempotency key belongs to a different request'),
    ).toBeVisible();
    expect(key).toHaveValue('existing-request-001');
    expect(screen.getByRole('button', { name: 'Generate new sync request key' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Generate new sync request key' }));
    await user.click(screen.getByRole('button', { name: 'Submit idempotent job' }));
    expect(await screen.findByText('Job accepted with an idempotency key.')).toBeVisible();
    expect(client.submitJob).toHaveBeenCalledTimes(2);
  });

  it('rejects malformed provider references and short request keys at the form boundary', async () => {
    const user = userEvent.setup();
    const client = fakeClient([account], [job]);
    render(<App client={client} />);
    await screen.findByText('Console ready.');
    const reference = screen.getByLabelText('Provider account reference');
    await user.clear(reference);
    await user.type(reference, 'bad reference');
    expect(reference).toBeInvalid();
    await user.click(screen.getByRole('button', { name: 'Link account' }));
    expect(client.createAccount).not.toHaveBeenCalled();
    const key = screen.getByLabelText('Sync request key');
    await user.clear(key);
    await user.type(key, 'short');
    expect(key).toBeInvalid();
    await user.click(screen.getByRole('button', { name: 'Submit idempotent job' }));
    expect(client.submitJob).not.toHaveBeenCalled();
  });

  it('runs the real HTTP client through the button without losing its method binding', async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      const path = new URL(url).pathname;
      const payload = path.endsWith('/connectors')
        ? [{ id: 'atlas-ads', displayName: 'Atlas Ads' }]
        : path.endsWith('/accounts')
          ? [account]
          : path.endsWith('/dashboard')
            ? { statusCounts: { QUEUED: 1 }, recentJobs: [job] }
            : path.endsWith('/run-next')
              ? { job, workAvailable: true }
              : { items: [job], nextCursor: null };
      return Promise.resolve(
        new Response(JSON.stringify(payload), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<App client={new HttpOperationsClient('https://operations.example.test/')} />);
    await screen.findByText('Console ready.');
    await userEvent.setup().click(screen.getByRole('button', { name: 'Run next eligible job' }));
    expect(await screen.findByText('Scheduler processed the next eligible page.')).toBeVisible();
    expect(fetchMock).toHaveBeenCalledWith(
      'https://operations.example.test/api/v1/jobs/run-next',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'X-Demo-Role': 'operator',
          'X-Demo-Actor': 'web-console',
        }),
      }),
    );
    expect(
      fetchMock.mock.calls.filter(
        (call) => call[0] === 'https://operations.example.test/api/v1/dashboard',
      ),
    ).toHaveLength(2);
  });

  it('recovers busy controls after a non-Error mutation failure', async () => {
    const user = userEvent.setup();
    const client = fakeClient([account], [job]);
    vi.mocked(client.runNext).mockRejectedValueOnce('provider disconnected');
    render(<App client={client} />);
    await screen.findByText('Console ready.');
    await user.click(screen.getByRole('button', { name: 'Run next eligible job' }));
    expect(await screen.findByText('Error: Unexpected operation failure')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Run next eligible job' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Submit idempotent job' })).toBeEnabled();
  });
});
