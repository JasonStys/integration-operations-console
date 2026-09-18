/**
 * File: client.test.ts
 * Purpose: Verifies request paths, mutation headers, JSON decoding, and problem handling.
 * Symbols: response fixtures and HttpOperationsClient tests; exact lines are in docs/code-index.md.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';

import { HttpOperationsClient, type SyncJob } from './client';

const job: SyncJob = {
  id: '11111111-1111-1111-1111-111111111111',
  accountId: '22222222-2222-2222-2222-222222222222',
  status: 'QUEUED',
  failurePlan: 'NONE',
  attempt: 0,
  maxAttempts: 3,
  pageCursor: null,
  availableAt: '2026-09-17T12:00:00Z',
  correlationId: 'test-correlation',
  idempotencyKey: 'test-key-123',
  lastError: null,
  recordsProcessed: 0,
  createdAt: '2026-09-17T12:00:00Z',
  updatedAt: '2026-09-17T12:00:00Z',
};

afterEach(() => vi.unstubAllGlobals());

describe('HttpOperationsClient', () => {
  it('uses the configured base URL and decodes a successful response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ id: 'atlas-ads', displayName: 'Atlas' }]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const result = await new HttpOperationsClient('https://example.test/').connectors();

    expect(result).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith(
      'https://example.test/api/v1/connectors',
      expect.objectContaining({ headers: expect.objectContaining({ Accept: 'application/json' }) }),
    );
  });

  it('adds explicit operator and idempotency headers to mutations', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ resource: job, created: true }), { status: 202 }),
      );
    vi.stubGlobal('fetch', fetchMock);

    await new HttpOperationsClient().submitJob(
      { accountId: job.accountId, failurePlan: 'NONE', maxAttempts: 3 },
      'idempotency-123',
    );

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/jobs',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          'Idempotency-Key': 'idempotency-123',
          'X-Demo-Role': 'operator',
        }),
      }),
    );
  });

  it('maps every job action and account/audit read to its contract path', async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      const body = url.endsWith('/audit') ? [] : url.endsWith('/accounts') ? [] : job;
      return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const client = new HttpOperationsClient();

    await client.accounts();
    await client.runJob(job.id);
    await client.cancelJob(job.id);
    await client.replayJob(job.id);
    await client.audit(job.id);

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/v1/accounts',
      `/api/v1/jobs/${job.id}/run`,
      `/api/v1/jobs/${job.id}/cancel`,
      `/api/v1/jobs/${job.id}/replay`,
      `/api/v1/jobs/${job.id}/audit`,
    ]);
  });

  it('surfaces safe RFC problem detail text', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: 'State conflict', detail: 'job is delayed' }), {
          status: 409,
        }),
      ),
    );

    await expect(new HttpOperationsClient().runNext()).rejects.toThrow('job is delayed');
  });

  it('falls back to the status when an error body is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('bad gateway', { status: 502 })));

    await expect(new HttpOperationsClient().dashboard()).rejects.toThrow('Request failed with 502');
  });
});
