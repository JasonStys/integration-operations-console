/**
 * File: JobReview.test.tsx
 * Purpose: Exercise full-list filters, captured-history navigation/export and stale-response protection.
 * Author: Jason Stys. Variables: disposable client fixtures and deferred promises model independent requests.
 * Symbols and line locations: docs/code-index.md. No real provider or database is contacted.
 */
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { JobReview } from './JobReview';
import type {
  ConnectorAccount,
  Dashboard,
  JobInspection,
  JobPage,
  OperationsClient,
  SyncJob,
} from './api/client';

const account: ConnectorAccount = {
  id: '22222222-2222-2222-2222-222222222222',
  connectorId: 'atlas-ads',
  displayName: 'Regional service account',
  externalReference: 'regional-02',
  createdAt: '2026-09-17T12:00:00Z',
};
const job: SyncJob = {
  id: '11111111-1111-1111-1111-111111111111',
  accountId: account.id,
  status: 'RETRY_WAIT',
  failurePlan: 'RATE_LIMIT_ONCE',
  attempt: 1,
  maxAttempts: 3,
  pageCursor: 'provider-page-2',
  availableAt: '2026-09-17T12:00:05Z',
  correlationId: 'review-fixture',
  idempotencyKey: 'review-key-01',
  lastError: 'simulated provider rate limit',
  recordsProcessed: 40,
  createdAt: '2026-09-17T12:00:00Z',
  updatedAt: '2026-09-17T12:00:02Z',
};
const otherJob: SyncJob = {
  ...job,
  id: '33333333-3333-3333-3333-333333333333',
  status: 'SUCCEEDED',
  recordsProcessed: 80,
};
const revision: Dashboard = { statusCounts: {}, recentJobs: [] };

/** Construct real captured-state versus historical-event distinctions, independently of component logic. */
function capture(overrides: Partial<JobInspection> = {}): JobInspection {
  return {
    job,
    capturedAt: '2026-09-17T12:00:03Z',
    nextEligibleDelayMs: 2000,
    history: {
      items: [
        {
          id: 8,
          jobId: job.id,
          eventType: 'RETRY_SCHEDULED',
          actor: 'scheduler',
          detail: 'Provider rate limited; retry scheduled in 5000 ms',
          occurredAt: '2026-09-17T12:00:00Z',
        },
      ],
      nextBefore: '8',
    },
    notice:
      'Captured state is current at capture time; older events do not reconstruct historical state.',
    ...overrides,
  };
}

/** Minimal complete client boundary: read-only review must never call any mutation. */
function clientFixture(): OperationsClient {
  return {
    connectors: vi.fn().mockResolvedValue([]),
    accounts: vi.fn().mockResolvedValue([account]),
    dashboard: vi.fn().mockResolvedValue(revision),
    createAccount: vi.fn(),
    submitJob: vi.fn(),
    runNext: vi.fn(),
    runJob: vi.fn(),
    cancelJob: vi.fn(),
    replayJob: vi.fn(),
    audit: vi.fn(),
    jobs: vi.fn().mockResolvedValue({ items: [job], nextCursor: null }),
    inspection: vi.fn().mockResolvedValue(capture()),
  };
}

/** Controlled completion order models responses arriving after changed filters/context. */
function deferred<T>() {
  let resolve!: (result: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((accept, fail) => {
    resolve = accept;
    reject = fail;
  });
  return { promise, resolve, reject };
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('JobReview acceptance', () => {
  it('reviews jobs beyond the recent feed and resets live keyset pages when filters change', async () => {
    const user = userEvent.setup();
    const client = clientFixture();
    vi.mocked(client.jobs)
      .mockResolvedValueOnce({ items: [job], nextCursor: 'opaque-page-2' })
      .mockResolvedValueOnce({ items: [otherJob], nextCursor: null })
      .mockResolvedValueOnce({ items: [job], nextCursor: 'opaque-page-2' })
      .mockResolvedValueOnce({ items: [otherJob], nextCursor: null });
    render(
      <JobReview
        client={client}
        accounts={[account]}
        requestedJobId={null}
        revision={revision}
        busy={false}
      />,
    );
    expect(await screen.findByRole('button', { name: 'Review 11111111' })).toBeEnabled();
    expect(revision.recentJobs).toEqual([]);
    expect(client.jobs).toHaveBeenLastCalledWith('', '', null);
    expect(screen.getByRole('button', { name: 'Previous job page' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Next job page' }));
    await screen.findByText('1 jobs on page 2.');
    expect(client.jobs).toHaveBeenLastCalledWith('', '', 'opaque-page-2');
    expect(screen.getByRole('button', { name: 'Next job page' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Previous job page' }));
    await screen.findByText('1 jobs on page 1.');
    await user.selectOptions(screen.getByLabelText('Filter account'), account.id);
    await user.selectOptions(screen.getByLabelText('Filter state'), 'SUCCEEDED');
    expect(client.jobs).toHaveBeenCalledTimes(3);
    await user.click(screen.getByRole('button', { name: 'Apply job filters' }));
    await screen.findByRole('button', { name: 'Review 33333333' });
    expect(client.jobs).toHaveBeenLastCalledWith(account.id, 'SUCCEEDED', null);
    expect(screen.getByRole('button', { name: 'Previous job page' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Review 33333333' }));
    await waitFor(() => expect(client.inspection).toHaveBeenCalledWith(otherJob.id, null));
    expect(client.runJob).not.toHaveBeenCalled();
    expect(client.replayJob).not.toHaveBeenCalled();
  });

  it.each([new Error('Job store unavailable'), 'not-an-error', new Error('')])(
    'shows a page failure and permits a new filter request without retaining old rows (%s)',
    async (reason) => {
      const client = clientFixture();
      vi.mocked(client.jobs)
        .mockRejectedValueOnce(reason)
        .mockResolvedValueOnce({ items: [], nextCursor: null });
      render(
        <JobReview
          client={client}
          accounts={[]}
          requestedJobId={null}
          revision={revision}
          busy={false}
        />,
      );
      const expected =
        reason instanceof Error && reason.message ? reason.message : 'Unable to load review';
      expect(await screen.findByText(expected)).toBeVisible();
      expect(screen.queryByRole('button', { name: /^Review / })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Next job page' })).toBeDisabled();
      await userEvent.setup().click(screen.getByRole('button', { name: 'Apply job filters' }));
      expect(await screen.findByText('No jobs match these filters.')).toBeVisible();
      expect(screen.queryByText(expected)).not.toBeInTheDocument();
    },
  );

  it('navigates captured windows and downloads exactly the displayed refresh without another read', async () => {
    const user = userEvent.setup();
    const client = clientFixture();
    const older = capture({
      history: {
        items: [
          {
            id: 2,
            jobId: job.id,
            eventType: 'JOB_SUBMITTED',
            actor: 'operator',
            detail: 'Original submission, not current state',
            occurredAt: '2026-09-16T12:00:00Z',
          },
        ],
        nextBefore: null,
      },
    });
    const refreshed = capture({
      job: { ...job, status: 'SUCCEEDED', recordsProcessed: 80, pageCursor: null },
      capturedAt: '2026-09-17T12:01:00Z',
      nextEligibleDelayMs: null,
      history: { items: [], nextBefore: null },
    });
    vi.mocked(client.inspection)
      .mockResolvedValueOnce(capture())
      .mockResolvedValueOnce(older)
      .mockResolvedValueOnce(capture())
      .mockResolvedValueOnce(refreshed);
    render(
      <JobReview
        client={client}
        accounts={[account]}
        requestedJobId={job.id}
        revision={revision}
        busy={false}
      />,
    );
    expect(await screen.findByText('Availability delay at snapshot: 2000 ms.')).toBeVisible();
    expect(screen.getByText('Actor: scheduler')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Newest audit events' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Older audit events' }));
    expect(await screen.findByText('Original submission, not current state')).toBeVisible();
    expect(client.inspection).toHaveBeenLastCalledWith(job.id, '8');
    expect(screen.getByRole('button', { name: 'Older audit events' })).toBeDisabled();
    expect(screen.getByText('Availability delay at snapshot: 2000 ms.')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Newest audit events' }));
    await screen.findByText('RETRY SCHEDULED');
    expect(client.inspection).toHaveBeenLastCalledWith(job.id, null);
    await user.click(screen.getByRole('button', { name: 'Refresh captured job review' }));
    expect(await screen.findByText('This state is not runnable.')).toBeVisible();
    expect(screen.getByText('No recorded events in this window.')).toBeVisible();
    expect(screen.getByText('No older events beyond this window.')).toBeVisible();
    const create = vi.fn<(blob: Blob) => string>().mockReturnValue('blob:captured-review');
    const revoke = vi.fn();
    vi.stubGlobal(
      'URL',
      Object.assign(class extends URL {}, { createObjectURL: create, revokeObjectURL: revoke }),
    );
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    await user.click(screen.getByRole('button', { name: 'Download captured job review' }));
    expect(client.inspection).toHaveBeenCalledTimes(4);
    const blob = create.mock.calls[0]?.[0];
    expect(blob).toBeInstanceOf(Blob);
    if (!(blob instanceof Blob)) throw new Error('Expected captured JSON Blob');
    const text = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsText(blob);
    });
    expect(JSON.parse(text)).toEqual(refreshed);
    expect(click).toHaveBeenCalledOnce();
    const link = click.mock.instances[0];
    if (!(link instanceof HTMLAnchorElement)) throw new Error('Expected download anchor');
    expect(link.download).toBe(`integration-${job.id}-inspection.json`);
    expect(link.href).toBe('blob:captured-review');
    await waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:captured-review'));
  });

  it.each([new Error('Review temporarily unavailable'), { detail: 'not an Error' }, new Error('')])(
    'retries failed inspection and distinguishes zero delay from a terminal snapshot (%s)',
    async (reason) => {
      const client = clientFixture();
      vi.mocked(client.inspection)
        .mockRejectedValueOnce(reason)
        .mockResolvedValueOnce(capture({ nextEligibleDelayMs: 0 }));
      render(
        <JobReview
          client={client}
          accounts={[account]}
          requestedJobId={job.id}
          revision={revision}
          busy={false}
        />,
      );
      expect(await screen.findByRole('alert')).toHaveTextContent(
        reason instanceof Error && reason.message ? reason.message : 'Unable to load review',
      );
      expect(
        screen.queryByRole('button', { name: 'Download captured job review' }),
      ).not.toBeInTheDocument();
      await userEvent.setup().click(screen.getByRole('button', { name: 'Retry captured review' }));
      expect(await screen.findByText('Availability delay at snapshot: 0 ms.')).toBeVisible();
      expect(client.inspection).toHaveBeenCalledTimes(2);
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    },
  );

  it.each(['resolve', 'reject'] as const)(
    'ignores a stale page %s after newer filters have resolved',
    async (completion) => {
      const client = clientFixture();
      const old = deferred<JobPage>();
      const current = deferred<JobPage>();
      vi.mocked(client.jobs).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
      render(
        <JobReview
          client={client}
          accounts={[account]}
          requestedJobId={null}
          revision={revision}
          busy={false}
        />,
      );
      await userEvent.setup().click(screen.getByRole('button', { name: 'Apply job filters' }));
      await act(async () => current.resolve({ items: [otherJob], nextCursor: null }));
      expect(await screen.findByRole('button', { name: 'Review 33333333' })).toBeVisible();
      await act(async () => {
        if (completion === 'resolve') old.resolve({ items: [job], nextCursor: 'stale' });
        else old.reject(new Error('Stale page failure'));
      });
      expect(screen.getByRole('button', { name: 'Review 33333333' })).toBeVisible();
      expect(screen.queryByRole('button', { name: 'Review 11111111' })).not.toBeInTheDocument();
      expect(screen.queryByText('Stale page failure')).not.toBeInTheDocument();
    },
  );

  it('discards prior job/client/revision inspections and clears selection when applying filters', async () => {
    const client = clientFixture();
    const first = deferred<JobInspection>();
    const second = deferred<JobInspection>();
    vi.mocked(client.inspection)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const props = { client, accounts: [account], requestedJobId: job.id, revision, busy: false };
    const view = render(<JobReview {...props} />);
    await waitFor(() => expect(client.inspection).toHaveBeenCalledWith(job.id, null));
    const updatedRevision = { ...revision };
    view.rerender(<JobReview {...props} revision={updatedRevision} />);
    await waitFor(() => expect(client.inspection).toHaveBeenCalledTimes(2));
    await act(async () => first.resolve(capture({ notice: 'obsolete revision' })));
    expect(screen.queryByText('obsolete revision')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Download captured job review' }),
    ).not.toBeInTheDocument();
    const replacement = clientFixture();
    vi.mocked(replacement.inspection).mockResolvedValue(
      capture({ job: otherJob, notice: 'new client and job' }),
    );
    view.rerender(
      <JobReview
        {...props}
        client={replacement}
        requestedJobId={otherJob.id}
        revision={updatedRevision}
      />,
    );
    expect(await screen.findByText('new client and job')).toBeVisible();
    expect(replacement.inspection).toHaveBeenLastCalledWith(otherJob.id, null);
    await act(async () => second.reject(new Error('obsolete client failure')));
    expect(screen.queryByText('obsolete client failure')).not.toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: 'Apply job filters' }));
    expect(screen.queryByText('new client and job')).not.toBeInTheDocument();
    expect(screen.getByText(/Select Audit or Review on a job/)).toBeVisible();
    expect(
      screen.queryByRole('button', { name: 'Download captured job review' }),
    ).not.toBeInTheDocument();
  });

  it('hides stale captures and blocks review controls during a mutation, then recaptures after it', async () => {
    const client = clientFixture();
    const pending = deferred<JobInspection>();
    vi.mocked(client.inspection)
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValue(capture({ notice: 'Fresh after mutation' }));
    const props = { client, accounts: [account], requestedJobId: job.id, revision, busy: false };
    const view = render(<JobReview {...props} />);
    await screen.findByRole('button', { name: 'Review 11111111' });
    view.rerender(<JobReview {...props} busy />);
    expect(screen.getByRole('button', { name: 'Apply job filters' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Review 11111111' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next job page' })).toBeDisabled();
    await act(async () => pending.resolve(capture({ notice: 'Pre-mutation result' })));
    expect(screen.queryByText('Pre-mutation result')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Download captured job review' }),
    ).not.toBeInTheDocument();
    expect(client.inspection).toHaveBeenCalledTimes(1);
    view.rerender(<JobReview {...props} />);
    expect(await screen.findByText('Fresh after mutation')).toBeVisible();
    expect(client.inspection).toHaveBeenCalledTimes(2);
    expect(
      within(screen.getByRole('region', { name: 'Filtered integration jobs' })).getByRole(
        'button',
        { name: 'Review 11111111' },
      ),
    ).toBeEnabled();
  });
});
