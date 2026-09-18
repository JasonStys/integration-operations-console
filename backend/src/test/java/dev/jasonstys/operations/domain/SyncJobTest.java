/*
 * File: SyncJobTest.java
 * Purpose: Unit coverage for lifecycle invariants and guarded state transitions.
 * Symbols: fixed fixture factory and transition tests; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Exercises the highest-risk domain state machine without framework dependencies. */
class SyncJobTest {
    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    @Test
    void processesMultiplePagesAndSucceeds() {
        SyncJob queued = newJob();

        SyncJob afterPage = queued.start(NOW).continueWith("page-2", 3, NOW);
        SyncJob completed = afterPage.start(NOW).succeed(2, NOW);

        assertThat(completed.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(completed.recordsProcessed()).isEqualTo(5);
        assertThat(completed.pageCursor()).isNull();
    }

    @Test
    void retryConsumesBudgetAndPreservesCursor() {
        SyncJob running = newJob().start(NOW);

        SyncJob waiting = running.retry("temporary", NOW.plusSeconds(3), NOW);

        assertThat(waiting.status()).isEqualTo(JobStatus.RETRY_WAIT);
        assertThat(waiting.attempt()).isEqualTo(1);
        assertThat(waiting.availableAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(waiting.lastError()).isEqualTo("temporary");
    }

    @Test
    void replayOnlyAcceptsDeadLetteredJobs() {
        assertThatThrownBy(() -> newJob().replay(NOW))
                .isInstanceOf(IllegalStateException.class);

        SyncJob replayed = newJob().start(NOW).deadLetter("broken schema", NOW).replay(NOW);

        assertThat(replayed.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(replayed.attempt()).isZero();
        assertThat(replayed.lastError()).isNull();
    }

    @Test
    void terminalJobsCannotBeCancelledOrRestarted() {
        SyncJob succeeded = newJob().start(NOW).succeed(1, NOW);

        assertThatThrownBy(() -> succeeded.cancel(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> succeeded.start(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidCounters() {
        assertThatThrownBy(() -> new SyncJob(
                UUID.randomUUID(), UUID.randomUUID(), JobStatus.QUEUED, FailurePlan.NONE,
                -1, 3, null, NOW, "correlation", "idempotency", null, 0, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SyncJob newJob() {
        return SyncJob.queued(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                FailurePlan.NONE, 3, "test-correlation", "test-idempotency", NOW);
    }
}
