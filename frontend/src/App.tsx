/**
 * File: App.tsx
 * Purpose: Accessible operator workflow for linking fake accounts and exercising job reliability.
 * Symbols: App component, client prop, remote-state variables, form handlers, and job actions;
 * exact lines are generated in docs/code-index.md.
 * Variables: selected failure/account/job are UI state only; the API remains the source of truth.
 */
import { useCallback, useEffect, useId, useMemo, useState } from 'react';

import {
  apiClient,
  type AuditEvent,
  type ConnectorAccount,
  type ConnectorDescriptor,
  type Dashboard,
  type FailurePlan,
  type OperationsClient,
  type SyncJob,
} from './api/client';
import { StatusBadge } from './components/StatusBadge';

interface AppProps {
  client?: OperationsClient;
}

const failurePlans: Array<{ value: FailurePlan; label: string }> = [
  { value: 'NONE', label: 'Healthy provider' },
  { value: 'RATE_LIMIT_ONCE', label: 'Rate limit once' },
  { value: 'TRANSIENT_TWICE', label: 'Transient failure twice' },
  { value: 'PERMANENT', label: 'Permanent schema failure' },
];

/** Main console view with dependency injection for deterministic browser tests. */
export function App({ client = apiClient }: AppProps) {
  const accountNameId = useId();
  const connectorId = useId();
  const accountId = useId();
  const failureId = useId();
  const [connectors, setConnectors] = useState<ConnectorDescriptor[]>([]);
  const [accounts, setAccounts] = useState<ConnectorAccount[]>([]);
  const [dashboard, setDashboard] = useState<Dashboard>({ statusCounts: {}, recentJobs: [] });
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [selectedConnectorId, setSelectedConnectorId] = useState('');
  const [failurePlan, setFailurePlan] = useState<FailurePlan>('NONE');
  const [accountName, setAccountName] = useState('Demo Operations Account');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('Loading operational data…');
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const [connectorData, accountData, dashboardData] = await Promise.all([
      client.connectors(),
      client.accounts(),
      client.dashboard(),
    ]);
    setConnectors(connectorData);
    setAccounts(accountData);
    setDashboard(dashboardData);
    setSelectedConnectorId((current) => current || connectorData[0]?.id || '');
    setSelectedAccountId((current) => current || accountData[0]?.id || '');
  }, [client]);

  useEffect(() => {
    void refresh()
      .then(() => setMessage('Console ready.'))
      .catch((reason: unknown) => setError(errorMessage(reason)));
  }, [refresh]);

  const selectedJob = useMemo(
    () => dashboard.recentJobs.find((job) => job.id === selectedJobId) ?? null,
    [dashboard.recentJobs, selectedJobId],
  );

  async function execute(label: string, action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await refresh();
      setMessage(label);
    } catch (reason: unknown) {
      setError(errorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  async function createAccount(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await execute('Fake account is linked.', async () => {
      const result = await client.createAccount({
        connectorId: selectedConnectorId,
        displayName: accountName,
        externalReference: `demo-${selectedConnectorId}`,
      });
      setSelectedAccountId(result.resource.id);
    });
  }

  async function submitJob(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await execute('Job accepted with an idempotency key.', async () => {
      const idempotencyKey = `web-${selectedAccountId}-${failurePlan}`;
      const result = await client.submitJob(
        { accountId: selectedAccountId, failurePlan, maxAttempts: 3 },
        idempotencyKey,
      );
      setSelectedJobId(result.resource.id);
    });
  }

  async function showAudit(job: SyncJob) {
    setSelectedJobId(job.id);
    setError(null);
    try {
      setAuditEvents(await client.audit(job.id));
      setMessage(`Showing ${job.id.slice(0, 8)} audit history.`);
    } catch (reason: unknown) {
      setError(errorMessage(reason));
    }
  }

  return (
    <div className="app-shell">
      <header className="hero">
        <div>
          <p className="eyebrow">Reliability engineering portfolio</p>
          <h1>Integration Operations Console</h1>
          <p className="hero__copy">
            Exercise retries, rate limits, pagination, idempotency, cancellation, and dead-letter
            recovery against deterministic fictional providers.
          </p>
        </div>
        <div className="hero__signal" aria-label="Environment status">
          <span className="signal-dot" aria-hidden="true" />
          Local simulation
        </div>
      </header>

      <main id="main-content">
        <div className="notice" role="status" aria-live="polite">
          {error === null ? message : `Error: ${error}`}
        </div>

        <section aria-labelledby="status-heading">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Current workload</p>
              <h2 id="status-heading">Lifecycle overview</h2>
            </div>
            <button
              className="button button--primary"
              type="button"
              disabled={busy}
              onClick={() =>
                void execute('Scheduler processed the next eligible page.', client.runNext)
              }
            >
              Run next eligible job
            </button>
          </div>
          <div className="metric-grid">
            {Object.entries(dashboard.statusCounts).map(([status, count]) => (
              <article className="metric" key={status}>
                <span>{status.replace('_', ' ')}</span>
                <strong>{count}</strong>
              </article>
            ))}
          </div>
        </section>

        <section className="workflow-grid" aria-label="Create demonstration workload">
          <form className="panel" onSubmit={(event) => void createAccount(event)}>
            <p className="step">Step 1</p>
            <h2>Link a fake account</h2>
            <label htmlFor={connectorId}>Connector</label>
            <select
              id={connectorId}
              value={selectedConnectorId}
              onChange={(event) => setSelectedConnectorId(event.target.value)}
              required
            >
              {connectors.map((connector) => (
                <option value={connector.id} key={connector.id}>
                  {connector.displayName}
                </option>
              ))}
            </select>
            <label htmlFor={accountNameId}>Display name</label>
            <input
              id={accountNameId}
              value={accountName}
              maxLength={100}
              onChange={(event) => setAccountName(event.target.value)}
              required
            />
            <button className="button" type="submit" disabled={busy || selectedConnectorId === ''}>
              Link account
            </button>
          </form>

          <form className="panel" onSubmit={(event) => void submitJob(event)}>
            <p className="step">Step 2</p>
            <h2>Schedule a sync</h2>
            <label htmlFor={accountId}>Account</label>
            <select
              id={accountId}
              value={selectedAccountId}
              onChange={(event) => setSelectedAccountId(event.target.value)}
              required
            >
              <option value="" disabled>
                Link an account first
              </option>
              {accounts.map((account) => (
                <option value={account.id} key={account.id}>
                  {account.displayName}
                </option>
              ))}
            </select>
            <label htmlFor={failureId}>Failure scenario</label>
            <select
              id={failureId}
              value={failurePlan}
              onChange={(event) => setFailurePlan(event.target.value as FailurePlan)}
            >
              {failurePlans.map((plan) => (
                <option value={plan.value} key={plan.value}>
                  {plan.label}
                </option>
              ))}
            </select>
            <button className="button" type="submit" disabled={busy || selectedAccountId === ''}>
              Submit idempotent job
            </button>
          </form>
        </section>

        <section aria-labelledby="jobs-heading">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Keyset-paginated feed</p>
              <h2 id="jobs-heading">Recent jobs</h2>
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <caption className="visually-hidden">Recent integration synchronization jobs</caption>
              <thead>
                <tr>
                  <th scope="col">Job</th>
                  <th scope="col">State</th>
                  <th scope="col">Scenario</th>
                  <th scope="col">Attempts</th>
                  <th scope="col">Records</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {dashboard.recentJobs.length === 0 ? (
                  <tr>
                    <td colSpan={6}>No jobs yet. Link an account and schedule the first sync.</td>
                  </tr>
                ) : (
                  dashboard.recentJobs.map((job) => (
                    <tr key={job.id}>
                      <td className="mono" title={job.id}>
                        {job.id.slice(0, 8)}
                      </td>
                      <td>
                        <StatusBadge status={job.status} />
                      </td>
                      <td>{job.failurePlan.replaceAll('_', ' ').toLowerCase()}</td>
                      <td>
                        {job.attempt}/{job.maxAttempts}
                      </td>
                      <td>{job.recordsProcessed}</td>
                      <td>
                        <div className="action-row">
                          <button
                            className="link-button"
                            type="button"
                            onClick={() => void showAudit(job)}
                          >
                            Audit
                          </button>
                          {(job.status === 'QUEUED' || job.status === 'RETRY_WAIT') && (
                            <button
                              className="link-button"
                              type="button"
                              disabled={busy}
                              onClick={() =>
                                void execute('Job processed one page.', () => client.runJob(job.id))
                              }
                            >
                              Run
                            </button>
                          )}
                          {job.status === 'DEAD_LETTERED' && (
                            <button
                              className="link-button"
                              type="button"
                              disabled={busy}
                              onClick={() =>
                                void execute('Dead-lettered job requeued.', () =>
                                  client.replayJob(job.id),
                                )
                              }
                            >
                              Replay
                            </button>
                          )}
                          {!['SUCCEEDED', 'DEAD_LETTERED', 'CANCELLED'].includes(job.status) && (
                            <button
                              className="link-button link-button--danger"
                              type="button"
                              disabled={busy}
                              onClick={() =>
                                void execute('Job cancelled.', () => client.cancelJob(job.id))
                              }
                            >
                              Cancel
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel audit-panel" aria-labelledby="audit-heading">
          <p className="eyebrow">Append-only evidence</p>
          <h2 id="audit-heading">Audit trail</h2>
          {selectedJob === null ? (
            <p>Select Audit on a job to inspect each scheduler and operator decision.</p>
          ) : (
            <>
              <p className="mono">Job {selectedJob.id}</p>
              <ol className="timeline">
                {auditEvents.map((event) => (
                  <li key={event.id}>
                    <strong>{event.eventType.replaceAll('_', ' ')}</strong>
                    <span>{event.detail}</span>
                    <time dateTime={event.occurredAt}>
                      {new Date(event.occurredAt).toLocaleString()}
                    </time>
                  </li>
                ))}
              </ol>
            </>
          )}
        </section>
      </main>

      <footer>Fictional providers • deterministic failures • no external credentials</footer>
    </div>
  );
}

function errorMessage(reason: unknown): string {
  return reason instanceof Error ? reason.message : 'Unexpected operation failure';
}
