/*
 * File: OperationsStore.java
 * Purpose: Persistence port separating orchestration from relational implementation.
 * Symbols: account/job/audit commands and immutable result records; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.jasonstys.operations.domain.AuditEvent;
import dev.jasonstys.operations.domain.ConnectorAccount;
import dev.jasonstys.operations.domain.SyncJob;

/** Transaction-friendly durable operations boundary. */
public interface OperationsStore {
    /** Creates an account or returns the existing connector/reference pair. */
    AccountResult saveAccountIfAbsent(ConnectorAccount account);

    /** @return account by ID */
    Optional<ConnectorAccount> findAccount(UUID id);

    /** @return all accounts in creation order */
    List<ConnectorAccount> listAccounts();

    /** Creates a job or returns the job already associated with its idempotency key. */
    SubmissionResult saveJobIfAbsent(SyncJob job);

    /** @return job by ID */
    Optional<SyncJob> findJob(UUID id);

    /** @return earliest ready job, if any */
    Optional<SyncJob> findNextReady(Instant now);

    /** Replaces a job after a validated state transition. */
    void updateJob(SyncJob job);

    /** Returns a keyset-paginated job page. */
    JobPage listJobs(int limit, PageCursor cursor);

    /** Appends, but never updates, one audit event. */
    void appendAudit(AuditEvent event);

    /** @return chronological audit events for a job */
    List<AuditEvent> listAudit(UUID jobId);

    /** @return current counts grouped by lifecycle state */
    List<StatusCount> countByStatus();

    /** Account creation/deduplication result. */
    record AccountResult(ConnectorAccount account, boolean created) {
    }

    /** Job submission/deduplication result. */
    record SubmissionResult(SyncJob job, boolean created) {
    }

    /** One page and the cursor for the following page. */
    record JobPage(List<SyncJob> items, String nextCursor) {
        public JobPage {
            items = List.copyOf(items);
        }
    }

    /** Aggregate state count. */
    record StatusCount(String status, long count) {
    }
}
