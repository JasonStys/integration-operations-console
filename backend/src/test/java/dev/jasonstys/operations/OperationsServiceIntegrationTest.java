/*
 * File: OperationsServiceIntegrationTest.java
 * Purpose: Exercises Flyway, JDBC persistence, idempotency, pagination, and connector orchestration.
 * Symbols: service/store fixtures and end-to-end application-service scenarios;
 * exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.jasonstys.operations.domain.FailurePlan;
import dev.jasonstys.operations.domain.JobStatus;
import dev.jasonstys.operations.domain.SyncJob;
import dev.jasonstys.operations.service.OperationsService;
import dev.jasonstys.operations.store.OperationsStore;

/** Full application context against H2 PostgreSQL mode for fast local feedback. */
@SpringBootTest
class OperationsServiceIntegrationTest {
    @Autowired
    private OperationsService service;

    @Test
    void deduplicatesSubmissionAndProcessesEveryProviderPage() {
        String suffix = UUID.randomUUID().toString();
        UUID accountId = service.createAccount("atlas-ads", "Integration Test", "test-" + suffix)
                .account().id();

        OperationsStore.SubmissionResult first = service.submit(
                accountId, FailurePlan.NONE, 3, "correlation-a", "key-" + suffix, "test");
        OperationsStore.SubmissionResult duplicate = service.submit(
                accountId, FailurePlan.NONE, 3, "correlation-b", "key-" + suffix, "test");

        assertThat(first.created()).isTrue();
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.job().id()).isEqualTo(first.job().id());

        SyncJob pageOne = service.process(first.job().id());
        SyncJob complete = service.process(first.job().id());

        assertThat(pageOne.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(pageOne.recordsProcessed()).isEqualTo(3);
        assertThat(complete.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(complete.recordsProcessed()).isEqualTo(5);
        assertThat(service.audit(complete.id())).extracting("eventType")
                .containsExactly("JOB_SUBMITTED", "JOB_STARTED", "PAGE_ACCEPTED",
                        "JOB_STARTED", "JOB_SUCCEEDED");
    }

    @Test
    void permanentProviderFailureGoesDirectlyToDeadLetterAndCanReplay() {
        String suffix = UUID.randomUUID().toString();
        UUID accountId = service.createAccount("beacon-audience", "Failure Test", "failure-" + suffix)
                .account().id();
        SyncJob submitted = service.submit(
                accountId, FailurePlan.PERMANENT, 3, "correlation", "dead-" + suffix, "test")
                .job();

        SyncJob failed = service.process(submitted.id());
        SyncJob replayed = service.replay(failed.id(), "test-operator");

        assertThat(failed.status()).isEqualTo(JobStatus.DEAD_LETTERED);
        assertThat(failed.lastError()).contains("schema");
        assertThat(replayed.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(replayed.attempt()).isZero();
    }

    @Test
    void keysetPagesHaveNoOverlap() {
        for (int index = 0; index < 4; index++) {
            String suffix = UUID.randomUUID().toString();
            UUID accountId = service.createAccount(
                    "atlas-ads", "Paging " + index, "paging-" + suffix).account().id();
            service.submit(accountId, FailurePlan.NONE, 3, "paging", "page-" + suffix, "test");
        }

        OperationsStore.JobPage first = service.jobs(2, null);
        OperationsStore.JobPage second = service.jobs(2, first.nextCursor());

        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(second.items()).extracting(SyncJob::id)
                .doesNotContainAnyElementsOf(first.items().stream().map(SyncJob::id).toList());
    }

    @Test
    void rateLimitSchedulesRetryAndRecordsDecision() {
        String suffix = UUID.randomUUID().toString();
        UUID accountId = service.createAccount(
                "atlas-ads", "Rate Limit Test", "rate-" + suffix).account().id();
        SyncJob submitted = service.submit(
                accountId, FailurePlan.RATE_LIMIT_ONCE, 3,
                "rate-correlation", "rate-key-" + suffix, "test").job();
        Instant beforeProcessing = Instant.now();

        SyncJob waiting = service.process(submitted.id());

        assertThat(waiting.status()).isEqualTo(JobStatus.RETRY_WAIT);
        assertThat(waiting.attempt()).isEqualTo(1);
        assertThat(waiting.availableAt()).isAfter(beforeProcessing.plusSeconds(4));
        assertThat(service.audit(waiting.id())).extracting("eventType")
                .containsExactly("JOB_SUBMITTED", "JOB_STARTED", "RETRY_SCHEDULED");
    }

    @Test
    void exhaustedTransientFailureMovesDirectlyToDeadLetter() {
        String suffix = UUID.randomUUID().toString();
        UUID accountId = service.createAccount(
                "beacon-audience", "Exhaustion Test", "exhaust-" + suffix).account().id();
        SyncJob submitted = service.submit(
                accountId, FailurePlan.TRANSIENT_TWICE, 1,
                "exhaust-correlation", "exhaust-key-" + suffix, "test").job();

        SyncJob failed = service.process(submitted.id());

        assertThat(failed.status()).isEqualTo(JobStatus.DEAD_LETTERED);
        assertThat(failed.attempt()).isEqualTo(1);
        assertThat(failed.lastError()).contains("503");
    }

    @Test
    void cancellationIsTerminalAndAudited() {
        String suffix = UUID.randomUUID().toString();
        UUID accountId = service.createAccount(
                "atlas-ads", "Cancellation Test", "cancel-" + suffix).account().id();
        SyncJob submitted = service.submit(
                accountId, FailurePlan.NONE, 3,
                "cancel-correlation", "cancel-key-" + suffix, "test").job();

        SyncJob cancelled = service.cancel(submitted.id(), "test-operator");

        assertThat(cancelled.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(service.audit(cancelled.id())).extracting("eventType")
                .containsExactly("JOB_SUBMITTED", "JOB_CANCELLED");
    }
}
