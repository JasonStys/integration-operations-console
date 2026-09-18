/*
 * File: SyncJob.java
 * Purpose: Immutable synchronization job plus guarded lifecycle transitions.
 * Symbols: record components store identifiers, state, retry metadata, cursor,
 * diagnostics, counters, and timestamps; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent synchronization work item with state transitions enforced in one place.
 *
 * @param id job identifier
 * @param accountId linked account identifier
 * @param status current lifecycle state
 * @param failurePlan deterministic simulation behavior
 * @param attempt failed attempts already consumed
 * @param maxAttempts maximum failed attempts before dead-lettering
 * @param pageCursor provider continuation cursor, or null for the first/final page
 * @param availableAt earliest scheduler eligibility time
 * @param correlationId trace-friendly request identifier
 * @param idempotencyKey deduplication key supplied by the caller
 * @param lastError sanitized last failure, or null
 * @param recordsProcessed cumulative records accepted
 * @param createdAt creation time
 * @param updatedAt most recent transition time
 */
public record SyncJob(
        UUID id,
        UUID accountId,
        JobStatus status,
        FailurePlan failurePlan,
        int attempt,
        int maxAttempts,
        String pageCursor,
        Instant availableAt,
        String correlationId,
        String idempotencyKey,
        String lastError,
        int recordsProcessed,
        Instant createdAt,
        Instant updatedAt) {

    /** Rejects malformed state before it can reach persistence. */
    public SyncJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failurePlan, "failurePlan");
        Objects.requireNonNull(availableAt, "availableAt");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attempt < 0 || maxAttempts < 1 || attempt > maxAttempts) {
            throw new IllegalArgumentException("invalid retry counters");
        }
        if (recordsProcessed < 0) {
            throw new IllegalArgumentException("recordsProcessed must be non-negative");
        }
    }

    /**
     * Creates a newly queued job.
     *
     * @return initialized job with zero attempts and records
     */
    public static SyncJob queued(
            UUID id,
            UUID accountId,
            FailurePlan failurePlan,
            int maxAttempts,
            String correlationId,
            String idempotencyKey,
            Instant now) {
        return new SyncJob(id, accountId, JobStatus.QUEUED, failurePlan, 0, maxAttempts,
                null, now, correlationId, idempotencyKey, null, 0, now, now);
    }

    /** @return the job moved into RUNNING state */
    public SyncJob start(Instant now) {
        requireState(JobStatus.QUEUED, JobStatus.RETRY_WAIT);
        return copy(JobStatus.RUNNING, attempt, pageCursor, now, null, recordsProcessed, now);
    }

    /** @return the job queued for its next provider page */
    public SyncJob continueWith(String nextCursor, int acceptedRecords, Instant now) {
        requireState(JobStatus.RUNNING);
        if (nextCursor == null || nextCursor.isBlank()) {
            throw new IllegalArgumentException("nextCursor is required");
        }
        return copy(JobStatus.QUEUED, attempt, nextCursor, now, null,
                recordsProcessed + acceptedRecords, now);
    }

    /** @return the successfully completed job */
    public SyncJob succeed(int acceptedRecords, Instant now) {
        requireState(JobStatus.RUNNING);
        return copy(JobStatus.SUCCEEDED, attempt, null, now, null,
                recordsProcessed + acceptedRecords, now);
    }

    /** @return the job delayed until another automatic attempt */
    public SyncJob retry(String error, Instant retryAt, Instant now) {
        requireState(JobStatus.RUNNING);
        return copy(JobStatus.RETRY_WAIT, attempt + 1, pageCursor, retryAt,
                sanitizeError(error), recordsProcessed, now);
    }

    /** @return the job moved to the dead-letter queue */
    public SyncJob deadLetter(String error, Instant now) {
        requireState(JobStatus.RUNNING);
        return copy(JobStatus.DEAD_LETTERED, Math.min(attempt + 1, maxAttempts),
                pageCursor, now, sanitizeError(error), recordsProcessed, now);
    }

    /** @return a dead-lettered job reset for explicit operator replay */
    public SyncJob replay(Instant now) {
        requireState(JobStatus.DEAD_LETTERED);
        return copy(JobStatus.QUEUED, 0, pageCursor, now, null, recordsProcessed, now);
    }

    /** @return a cancellable job moved into terminal CANCELLED state */
    public SyncJob cancel(Instant now) {
        if (status.terminal()) {
            throw new IllegalStateException("terminal jobs cannot be cancelled");
        }
        return copy(JobStatus.CANCELLED, attempt, pageCursor, now, null, recordsProcessed, now);
    }

    /** @return true when another failed attempt would exhaust the retry budget */
    public boolean nextFailureExhaustsAttempts() {
        return attempt + 1 >= maxAttempts;
    }

    private SyncJob copy(
            JobStatus nextStatus,
            int nextAttempt,
            String nextCursor,
            Instant nextAvailableAt,
            String nextError,
            int nextRecordsProcessed,
            Instant nextUpdatedAt) {
        return new SyncJob(id, accountId, nextStatus, failurePlan, nextAttempt, maxAttempts,
                nextCursor, nextAvailableAt, correlationId, idempotencyKey, nextError,
                nextRecordsProcessed, createdAt, nextUpdatedAt);
    }

    private void requireState(JobStatus... allowed) {
        for (JobStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("transition is not valid from " + status);
    }

    private static String sanitizeError(String error) {
        String safe = Objects.requireNonNullElse(error, "unspecified provider failure");
        return safe.substring(0, Math.min(safe.length(), 300));
    }
}
