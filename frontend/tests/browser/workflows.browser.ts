/**
 * File: workflows.browser.ts
 * Purpose: Exercise real compiled React UI against owned Java/H2, including filtering, audit windows and downloads.
 * Symbols: ops fixture, real JSON helpers and seven acceptance scenarios; docs/code-index.md lists lines.
 * Variables: account/request keys are unique synthetic data; cleanup mutates only jobs made by each test.
 */
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import AxeBuilder from '@axe-core/playwright';
import {
  test as base,
  expect,
  type APIRequestContext,
  type APIResponse,
  type Page,
} from '@playwright/test';
import type { components } from '../../src/api/schema';
type AccountMutationResult = components['schemas']['AccountMutationResult'];
type ConnectorAccount = components['schemas']['ConnectorAccount'];
type FailurePlan = components['schemas']['FailurePlan'];
type JobInspection = components['schemas']['JobInspection'];
type JobMutationResult = components['schemas']['JobMutationResult'];
type JobPage = components['schemas']['JobPage'];
type RunNextResult = components['schemas']['RunNextResult'];
type SyncJob = components['schemas']['SyncJob'];

const OPERATOR = { 'X-Demo-Role': 'operator', 'X-Demo-Actor': 'browser-fixture' };
type Ops = {
  account: () => Promise<ConnectorAccount>;
  submit: (accountId: string, plan?: FailurePlan) => Promise<SyncJob>;
  track: (job: SyncJob) => void;
};

/** Require actual successful HTTP JSON; failures include bounded public fixture diagnostics. */
async function json<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), `${response.status()} ${(await response.text()).slice(0, 1200)}`).toBe(
    true,
  );
  return (await response.json()) as T;
}
/** Perform a real state change under the explicitly simulated operator header. */
async function mutate<T>(request: APIRequestContext, path: string): Promise<T> {
  return json<T>(await request.post(`/api/v1${path}`, { headers: OPERATOR }));
}
/** Restrict user-facing rows to one synthetic account using the actual UI form. */
async function filterAccount(page: Page, accountId: string, state = ''): Promise<void> {
  await page.getByLabel('Filter account', { exact: true }).selectOption(accountId);
  await page.getByLabel('Filter state', { exact: true }).selectOption(state);
  const filtered = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === '/api/v1/jobs' &&
      url.searchParams.get('accountId') === accountId &&
      (url.searchParams.get('status') ?? '') === state
    );
  });
  await page.getByRole('button', { name: 'Apply job filters', exact: true }).click();
  const result = (await (await filtered).json()) as JobPage;
  const expectedIds = result.items.map((job) => job.id);
  await expect
    .poll(() =>
      page
        .getByRole('region', { name: 'Filtered integration jobs' })
        .locator('tbody tr td:first-child')
        .allTextContents(),
    )
    .toEqual(expectedIds);
  await expect(
    page.getByText(`${expectedIds.length} jobs on page 1.`, { exact: true }),
  ).toBeVisible();
}
const test = base.extend<{ ops: Ops }>({
  ops: async ({ request }, use) => {
    const jobs = new Set<string>();
    const ops: Ops = {
      account: async () => {
        const key = randomUUID();
        const result = await json<AccountMutationResult>(
          await request.post('/api/v1/accounts', {
            headers: OPERATOR,
            data: {
              connectorId: 'atlas-ads',
              displayName: `Browser ${key}`,
              externalReference: `browser-${key}`,
            },
          }),
        );
        return result.resource;
      },
      submit: async (accountId, failurePlan = 'NONE') => {
        const result = await json<JobMutationResult>(
          await request.post('/api/v1/jobs', {
            headers: { ...OPERATOR, 'Idempotency-Key': `browser-${randomUUID()}` },
            data: { accountId, failurePlan, maxAttempts: 3 },
          }),
        );
        jobs.add(result.resource.id);
        return result.resource;
      },
      track: (job) => {
        jobs.add(job.id);
      },
    };
    try {
      await use(ops);
    } finally {
      for (const id of jobs) {
        const result = await request.get(`/api/v1/jobs/${id}`);
        if (!result.ok()) continue;
        const job = (await result.json()) as SyncJob;
        if (!['SUCCEEDED', 'CANCELLED', 'DEAD_LETTERED'].includes(job.status)) {
          expect(
            (await request.post(`/api/v1/jobs/${id}/cancel`, { headers: OPERATOR })).ok(),
          ).toBe(true);
        }
      }
    }
  },
});

test('operator links an account, deduplicates submission and processes both real provider pages via Run next', async ({
  page,
  request,
  ops,
}) => {
  await page.goto('/');
  await expect(page.getByRole('status').first()).toHaveText('Console ready.');
  const key = randomUUID();
  await page.getByLabel('Connector', { exact: true }).selectOption('atlas-ads');
  await page.getByLabel('Display name', { exact: true }).fill(`Browser UI ${key}`);
  await page.getByLabel('Provider account reference', { exact: true }).fill(`ui-${key}`);
  const accountResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v1/accounts') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Link account', exact: true }).click();
  const account = (await (await accountResponse).json()) as AccountMutationResult;
  await expect(page.getByRole('status').first()).toHaveText('Fake account is linked.');
  await page.getByLabel('Sync request key', { exact: true }).fill(`browser-${key}`);
  const firstResponse = page.waitForResponse(
    (response) => response.url().endsWith('/api/v1/jobs') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Submit idempotent job', exact: true }).click();
  const first = (await (await firstResponse).json()) as JobMutationResult;
  ops.track(first.resource);
  expect(first).toMatchObject({
    created: true,
    resource: { accountId: account.resource.id, status: 'QUEUED' },
  });
  await expect(page.getByRole('status').first()).toHaveText(
    'Job accepted with an idempotency key.',
  );
  const duplicateResponse = page.waitForResponse(
    (response) => response.url().endsWith('/api/v1/jobs') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Submit idempotent job', exact: true }).click();
  const duplicate = (await (await duplicateResponse).json()) as JobMutationResult;
  expect(duplicate.created).toBe(false);
  expect(duplicate.resource.id).toBe(first.resource.id);
  for (const expectedRecords of [3, 5]) {
    const runResponse = page.waitForResponse((response) =>
      response.url().endsWith('/api/v1/jobs/run-next'),
    );
    await page.getByRole('button', { name: 'Run next eligible job', exact: true }).click();
    const processed = (await (await runResponse).json()) as RunNextResult;
    expect(processed).toMatchObject({
      workAvailable: true,
      job: { id: first.resource.id, recordsProcessed: expectedRecords },
    });
    await expect(page.getByRole('status').first()).toHaveText(
      'Scheduler processed the next eligible page.',
    );
  }
  expect(await json<SyncJob>(await request.get(`/api/v1/jobs/${first.resource.id}`))).toMatchObject(
    { status: 'SUCCEEDED', recordsProcessed: 5, attempt: 0 },
  );
  await filterAccount(page, account.resource.id, 'SUCCEEDED');
  await expect(
    page.getByRole('region', { name: 'Filtered integration jobs' }).getByRole('row'),
  ).toHaveCount(2);
});

test('actual browser constraints reject bad references/keys and render an HTML-like display name as plain text', async ({
  page,
  ops,
}) => {
  await page.goto('/');
  await expect(page.getByRole('status').first()).toHaveText('Console ready.');
  const posts: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'POST') posts.push(request.url());
  });
  await page.getByLabel('Display name', { exact: true }).fill('Browser <b>literal fixture</b>');
  const reference = page.getByLabel('Provider account reference', { exact: true });
  await reference.fill('bad reference');
  expect(
    await reference.evaluate((element) => (element as HTMLInputElement).validity.patternMismatch),
  ).toBe(true);
  await page.getByRole('button', { name: 'Link account', exact: true }).click();
  expect(posts).toHaveLength(0);
  await reference.fill(`valid-${randomUUID()}`);
  const linked = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v1/accounts') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Link account', exact: true }).click();
  const account = (await (await linked).json()) as AccountMutationResult;
  await expect(page.getByRole('status').first()).toHaveText('Fake account is linked.');
  await expect(
    page.getByLabel('Account', { exact: true }).locator(`option[value="${account.resource.id}"]`),
  ).toHaveText('Browser <b>literal fixture</b>');
  await expect(page.locator('b')).toHaveCount(0);
  const key = page.getByLabel('Sync request key', { exact: true });
  await key.fill('invalid key with spaces');
  expect(
    await key.evaluate((element) => (element as HTMLInputElement).validity.patternMismatch),
  ).toBe(true);
  await page.getByRole('button', { name: 'Submit idempotent job', exact: true }).click();
  expect(posts).toHaveLength(1);
  await page.getByRole('button', { name: 'Generate new sync request key', exact: true }).click();
  expect(await key.inputValue()).not.toContain(' ');
  const submitted = page.waitForResponse(
    (response) => response.url().endsWith('/api/v1/jobs') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Submit idempotent job', exact: true }).click();
  const job = (await (await submitted).json()) as JobMutationResult;
  ops.track(job.resource);
  expect(job.created).toBe(true);
});

test('filtered review reaches jobs beyond recent ten with stable nonduplicated keyset pages', async ({
  page,
  request,
  ops,
}) => {
  const account = await ops.account();
  const jobs: SyncJob[] = [];
  for (let index = 0; index < 22; index += 1) jobs.push(await ops.submit(account.id));
  const cancelled = jobs[10];
  const oldest = jobs[0];
  if (!cancelled || !oldest) throw new Error('missing fixture job');
  await mutate(request, `/jobs/${cancelled.id}/cancel`);
  const dashboard = await json<{ recentJobs: SyncJob[] }>(await request.get('/api/v1/dashboard'));
  expect(dashboard.recentJobs.map((job) => job.id)).not.toContain(oldest.id);
  await page.goto('/');
  await expect(page.getByRole('status').first()).toHaveText('Console ready.');
  await filterAccount(page, account.id, 'QUEUED');
  const table = page.getByRole('region', { name: 'Filtered integration jobs' });
  const firstIds = await table.locator('tbody tr td:first-child').allTextContents();
  expect(firstIds).toHaveLength(20);
  await page.getByRole('button', { name: 'Next job page', exact: true }).click();
  await expect(page.getByText('1 jobs on page 2.', { exact: true })).toBeVisible();
  const secondIds = await table.locator('tbody tr td:first-child').allTextContents();
  const expected = jobs
    .map((job) => job.id)
    .filter((id) => id !== cancelled.id)
    .sort();
  expect([...firstIds, ...secondIds].sort()).toEqual(expected);
  expect(new Set([...firstIds, ...secondIds]).size).toBe(21);
  await expect(page.getByRole('button', { name: 'Next job page', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Previous job page', exact: true }).click();
  await expect(page.getByText('20 jobs on page 1.', { exact: true })).toBeVisible();
  await filterAccount(page, account.id, 'CANCELLED');
  await expect(table.locator('tbody tr')).toHaveCount(1);
  await expect(table).toContainText(cancelled.id);
  const response = await request.get(`/api/v1/jobs?accountId=${account.id}&status=QUEUED&limit=20`);
  const cursor = (await json<JobPage>(response)).nextCursor;
  expect(cursor).not.toBeNull();
  const otherScope = await request.get(
    `/api/v1/jobs?accountId=${account.id}&status=CANCELLED&cursor=${encodeURIComponent(cursor ?? '')}`,
  );
  expect(otherScope.status()).toBe(400);
});

test('captured audit windows and download are genuine bounded snapshots with no extra query or mutation', async ({
  page,
  request,
  ops,
}) => {
  const account = await ops.account();
  const job = await ops.submit(account.id, 'PERMANENT');
  for (let index = 0; index < 12; index += 1) {
    const dead = await mutate<SyncJob>(request, `/jobs/${job.id}/run`);
    expect(dead.status).toBe('DEAD_LETTERED');
    if (index < 11) await mutate(request, `/jobs/${job.id}/replay`);
  }
  const prior = await json<SyncJob>(await request.get(`/api/v1/jobs/${job.id}`));
  const originalAudit = await json<unknown[]>(await request.get(`/api/v1/jobs/${job.id}/audit`));
  expect(originalAudit).toHaveLength(36);
  await page.goto('/');
  await expect(page.getByRole('status').first()).toHaveText('Console ready.');
  await filterAccount(page, account.id, 'DEAD_LETTERED');
  const responses: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/inspection')) responses.push(request.method());
  });
  const capturedResponse = page.waitForResponse((response) =>
    response.url().includes(`/jobs/${job.id}/inspection`),
  );
  await page.getByRole('button', { name: `Review ${job.id.slice(0, 8)}`, exact: true }).click();
  const capture = (await (await capturedResponse).json()) as JobInspection;
  await expect(
    page.getByRole('button', { name: 'Download captured job review', exact: true }),
  ).toBeVisible();
  expect(capture.job).toEqual(prior);
  expect(capture.history.items).toHaveLength(25);
  expect(capture.history.nextBefore).not.toBeNull();
  expect(capture.nextEligibleDelayMs).toBeNull();
  await expect(page.getByText('This state is not runnable.', { exact: true })).toBeVisible();
  await expect(page.locator('.timeline li')).toHaveCount(25);
  const queryCount = responses.length;
  const downloaded = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download captured job review', exact: true }).click();
  const download = await downloaded;
  const path = await download.path();
  if (path === null) throw new Error('download missing');
  expect(JSON.parse(await readFile(path, 'utf8'))).toEqual(capture);
  expect(responses.length).toBe(queryCount);
  expect(responses.every((method) => method === 'GET')).toBe(true);
  const olderResponse = page.waitForResponse(
    (response) => response.url().includes('/inspection?') && response.url().includes('before='),
  );
  await page.getByRole('button', { name: 'Older audit events', exact: true }).click();
  const older = (await (await olderResponse).json()) as JobInspection;
  await expect(page.locator('.timeline li')).toHaveCount(11);
  expect(older.job).toEqual(prior);
  expect(older.history.nextBefore).toBeNull();
  await expect(
    page.getByRole('button', { name: 'Older audit events', exact: true }),
  ).toBeDisabled();
  expect(Math.max(...older.history.items.map((item) => item.id))).toBeLessThan(
    Math.min(...capture.history.items.map((item) => item.id)),
  );
  await page.getByRole('button', { name: 'Newest audit events', exact: true }).click();
  await expect(page.locator('.timeline li')).toHaveCount(25);
  expect(await json<SyncJob>(await request.get(`/api/v1/jobs/${job.id}`))).toEqual(prior);
  expect(await json<unknown[]>(await request.get(`/api/v1/jobs/${job.id}/audit`))).toEqual(
    originalAudit,
  );
});

test('viewer HTTP reads do not require the simulated operator header and state changes fail closed', async ({
  request,
  ops,
}) => {
  const account = await ops.account();
  const job = await ops.submit(account.id);
  const before = await json<SyncJob>(await request.get(`/api/v1/jobs/${job.id}`));
  const audit = await json<unknown[]>(await request.get(`/api/v1/jobs/${job.id}/audit`));
  expect((await request.get(`/api/v1/jobs/${job.id}/inspection?limit=1`)).ok()).toBe(true);
  for (const operation of ['run', 'cancel', 'replay']) {
    expect(
      (
        await request.post(`/api/v1/jobs/${job.id}/${operation}`, {
          headers: { 'X-Demo-Role': 'viewer' },
        })
      ).status(),
    ).toBe(403);
  }
  expect((await request.post('/api/v1/jobs/run-next')).status()).toBe(403);
  expect(await json<SyncJob>(await request.get(`/api/v1/jobs/${job.id}`))).toEqual(before);
  expect(await json<unknown[]>(await request.get(`/api/v1/jobs/${job.id}/audit`))).toEqual(audit);
});

test('rate-limited job review explains delay and explicit cancellation through actual quick action', async ({
  page,
  request,
  ops,
}) => {
  const account = await ops.account();
  const job = await ops.submit(account.id, 'RATE_LIMIT_ONCE');
  expect(await mutate<SyncJob>(request, `/jobs/${job.id}/run`)).toMatchObject({
    status: 'RETRY_WAIT',
    recordsProcessed: 0,
  });
  const delayed = await json<JobInspection>(await request.get(`/api/v1/jobs/${job.id}/inspection`));
  expect(delayed.nextEligibleDelayMs).toBeGreaterThan(0);
  expect(delayed.nextEligibleDelayMs).toBeLessThanOrEqual(5000);
  await page.goto('/');
  await expect(page.getByRole('status').first()).toHaveText('Console ready.');
  await filterAccount(page, account.id, 'RETRY_WAIT');
  await page.getByRole('button', { name: `Review ${job.id.slice(0, 8)}`, exact: true }).click();
  await expect(page.getByText(/Availability delay at snapshot:/u)).toBeVisible();
  await expect(page.getByText(/Zero delay does not predict provider availability/u)).toBeVisible();
  const row = page
    .getByRole('region', { name: 'Recent integration jobs' })
    .getByRole('row')
    .filter({ hasText: job.id.slice(0, 8) });
  await row.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.getByRole('status').first()).toHaveText('Job cancelled.');
  expect(await json<SyncJob>(await request.get(`/api/v1/jobs/${job.id}`))).toMatchObject({
    status: 'CANCELLED',
    recordsProcessed: 0,
  });
  await expect(page.getByText('No jobs match these filters.', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Next job page', exact: true })).toBeDisabled();
});

test('actual console has no detectable WCAG A/AA violations and no viewport-wide overflow', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.getByRole('status').first()).toHaveText('Console ready.');
  const width = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth,
  }));
  expect(width.content).toBeLessThanOrEqual(width.viewport);
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
    .analyze();
  expect(results.violations).toEqual([]);
});
