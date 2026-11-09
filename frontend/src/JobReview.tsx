/**
 * File: JobReview.tsx
 * Purpose: Filter every integration job and inspect bounded captured audit windows without mutation.
 * Author: Jason Stys. Symbols/variables: query/page/detail state and cancellation guards; docs/code-index.md.
 */
import { useEffect, useState } from 'react';
import type {
  ConnectorAccount,
  Dashboard,
  JobInspection,
  JobPage,
  JobStatus,
  OperationsClient,
} from './api/client';
import { StatusBadge } from './components/StatusBadge';

const statuses: JobStatus[] = [
  'QUEUED',
  'RUNNING',
  'RETRY_WAIT',
  'SUCCEEDED',
  'DEAD_LETTERED',
  'CANCELLED',
];
type Filters = { accountId: string; status: JobStatus | '' };
type PageState = {
  filter: Filters;
  cursors: (string | null)[];
  revision: Dashboard;
  client: OperationsClient;
  result: JobPage | null;
  error: string;
};
type DetailState = {
  id: string;
  before: string | null;
  revision: Dashboard;
  client: OperationsClient;
  refresh: number;
  result: JobInspection | null;
  error: string;
};

/** Read-only review preserves current state versus historical-event meaning and discards stale responses. */
export function JobReview({
  client,
  accounts,
  requestedJobId,
  revision,
  busy,
}: {
  client: OperationsClient;
  accounts: ConnectorAccount[];
  requestedJobId: string | null;
  revision: Dashboard;
  busy: boolean;
}) {
  const [draftAccount, setDraftAccount] = useState('');
  const [draftStatus, setDraftStatus] = useState<JobStatus | ''>('');
  const [filter, setFilter] = useState<Filters>({ accountId: '', status: '' });
  const [cursors, setCursors] = useState<(string | null)[]>([null]);
  const [pageState, setPageState] = useState<PageState | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [before, setBefore] = useState<string | null>(null);
  const [detail, setDetail] = useState<DetailState | null>(null);
  const [refreshDetail, setRefreshDetail] = useState(0);
  const pageMatches =
    pageState?.filter === filter &&
    pageState.cursors === cursors &&
    pageState.revision === revision &&
    pageState.client === client;
  const page = pageMatches
    ? (pageState.result ?? { items: [], nextCursor: null })
    : { items: [], nextCursor: null };
  const pageError = pageMatches ? pageState.error : '';
  const loading = !pageMatches || (pageState.result === null && !pageState.error);
  const detailMatches =
    detail?.id === selected &&
    detail.before === before &&
    detail.revision === revision &&
    detail.client === client &&
    detail.refresh === refreshDetail;
  const inspection = detailMatches && !busy ? detail.result : null;
  const detailError = detailMatches ? detail.error : '';
  const detailLoading =
    selected !== null && !busy && (!detailMatches || (detail.result === null && !detail.error));

  useEffect(() => {
    let active = true;
    const binding = { filter, cursors, revision, client };
    setPageState({ ...binding, result: null, error: '' });
    void client
      .jobs(filter.accountId, filter.status, cursors.at(-1) ?? null)
      .then((result) => {
        if (active) setPageState({ ...binding, result, error: '' });
      })
      .catch((error: unknown) => {
        if (active) setPageState({ ...binding, result: null, error: message(error) });
      });
    return () => {
      active = false;
    };
  }, [client, filter, cursors, revision]);

  useEffect(() => {
    setSelected(requestedJobId);
    setBefore(null);
  }, [requestedJobId]);

  useEffect(() => {
    let active = true;
    setDetail(null);
    if (selected !== null && !busy) {
      const binding = { id: selected, before, revision, client, refresh: refreshDetail };
      setDetail({ ...binding, result: null, error: '' });
      void client
        .inspection(selected, before)
        .then((result) => {
          if (active) setDetail({ ...binding, result, error: '' });
        })
        .catch((error: unknown) => {
          if (active) setDetail({ ...binding, result: null, error: message(error) });
        });
    }
    return () => {
      active = false;
    };
  }, [client, selected, before, revision, busy, refreshDetail]);

  /** Download exactly the displayed snapshot, without a second API query or original provider data. */
  function download() {
    if (inspection === null || busy || detailLoading) return;
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(inspection, null, 2)], { type: 'application/json' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = `integration-${inspection.job.id}-inspection.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  return (
    <>
      <section className="panel" aria-labelledby="search-heading">
        <h2 id="search-heading">Search all integration jobs</h2>
        <p>
          Filter the complete database, including jobs outside the recent activity feed. Pages are
          live keyset views; refresh after updates.
        </p>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            setFilter({ accountId: draftAccount, status: draftStatus });
            setCursors([null]);
            setSelected(null);
            setBefore(null);
          }}
        >
          <label htmlFor="review-account">Filter account</label>
          <select
            id="review-account"
            value={draftAccount}
            onChange={(event) => setDraftAccount(event.target.value)}
          >
            <option value="">All accounts</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.displayName}
              </option>
            ))}
          </select>
          <label htmlFor="review-status">Filter state</label>
          <select
            id="review-status"
            value={draftStatus}
            onChange={(event) => setDraftStatus(event.target.value as JobStatus | '')}
          >
            <option value="">All states</option>
            {statuses.map((state) => (
              <option key={state} value={state}>
                {state.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
          <button className="button" disabled={busy} type="submit">
            Apply job filters
          </button>
        </form>
        <p role="status">
          {loading
            ? 'Loading job page…'
            : pageError || `${page.items.length} jobs on page ${cursors.length}.`}
        </p>
        <div
          className="table-wrap"
          tabIndex={0}
          role="region"
          aria-label="Filtered integration jobs"
        >
          <table>
            <caption>Filtered job review</caption>
            <thead>
              <tr>
                <th scope="col">Job</th>
                <th scope="col">State</th>
                <th scope="col">Updated</th>
                <th scope="col">Review</th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((job) => (
                <tr key={job.id}>
                  <td className="mono">{job.id}</td>
                  <td>
                    <StatusBadge status={job.status} />
                  </td>
                  <td>
                    <time dateTime={job.updatedAt}>{new Date(job.updatedAt).toLocaleString()}</time>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="link-button"
                      disabled={busy}
                      onClick={() => {
                        setSelected(job.id);
                        setBefore(null);
                      }}
                    >
                      Review {job.id.slice(0, 8)}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!loading && !pageError && page.items.length === 0 && <p>No jobs match these filters.</p>}
        <nav aria-label="Filtered job pages">
          <button
            className="button"
            type="button"
            disabled={busy || loading || cursors.length === 1}
            onClick={() => setCursors((value) => value.slice(0, -1))}
          >
            Previous job page
          </button>
          <button
            className="button"
            type="button"
            disabled={busy || loading || page.nextCursor === null}
            onClick={() => {
              if (page.nextCursor !== null) setCursors((value) => [...value, page.nextCursor]);
            }}
          >
            Next job page
          </button>
        </nav>
      </section>
      <section className="panel audit-panel" aria-labelledby="audit-heading">
        <h2 id="audit-heading">Audit trail</h2>
        {detailLoading && <p role="status">Loading captured job review…</p>}
        {detailError && (
          <>
            <p role="alert">{detailError}</p>
            <button
              className="button"
              type="button"
              disabled={busy}
              onClick={() => setRefreshDetail((value) => value + 1)}
            >
              Retry captured review
            </button>
          </>
        )}
        {selected === null && (
          <p>
            Select Audit or Review on a job to inspect captured state and scheduler/operator
            decisions.
          </p>
        )}
        {inspection !== null && !busy && (
          <>
            <p className="mono">Job {inspection.job.id}</p>
            <p>
              Captured{' '}
              <time dateTime={inspection.capturedAt}>
                {new Date(inspection.capturedAt).toLocaleString()}
              </time>
              . State <StatusBadge status={inspection.job.status} />.{' '}
              {inspection.job.recordsProcessed} accepted records. Provider cursor:{' '}
              {inspection.job.pageCursor ?? 'none'}.
            </p>
            <p>
              {inspection.nextEligibleDelayMs === null
                ? 'This state is not runnable.'
                : `Availability delay at snapshot: ${inspection.nextEligibleDelayMs} ms.`}
            </p>
            <p>{inspection.notice}</p>
            <button className="button" type="button" onClick={download}>
              Download captured job review
            </button>
            <button
              className="button"
              type="button"
              onClick={() => setRefreshDetail((value) => value + 1)}
            >
              Refresh captured job review
            </button>
            <p>
              {inspection.history.nextBefore === null
                ? 'No older events beyond this window.'
                : 'Older events exist outside this window.'}
            </p>
            {inspection.history.items.length === 0 && <p>No recorded events in this window.</p>}
            <ol className="timeline">
              {inspection.history.items.map((event) => (
                <li key={event.id}>
                  <strong>{event.eventType.replaceAll('_', ' ')}</strong>
                  <span>{event.detail}</span>
                  <span>Actor: {event.actor}</span>
                  <time dateTime={event.occurredAt}>
                    {new Date(event.occurredAt).toLocaleString()}
                  </time>
                </li>
              ))}
            </ol>
            <button
              className="button"
              type="button"
              disabled={inspection.history.nextBefore === null}
              onClick={() => setBefore(inspection.history.nextBefore)}
            >
              Older audit events
            </button>
            <button
              className="button"
              type="button"
              disabled={before === null}
              onClick={() => setBefore(null)}
            >
              Newest audit events
            </button>
          </>
        )}
        <details>
          <summary>How to use the review</summary>
          <p>
            Reads work for demo viewers; mutations require the separate simulated operator header.
            This is not real authentication. Filters do not grant access. The timeline is a bounded
            event window; the state and delay describe the capture time, not the time of each old
            event. A zero delay does not mean a provider will accept a request. Refresh/filter
            changes may move live jobs between pages. No review automatically runs or replays work.
          </p>
        </details>
      </section>
    </>
  );
}

function message(error: unknown): string {
  return error instanceof Error && error.message ? error.message : 'Unable to load review';
}
