/*
 * File: OperationsReviewIntegrationTest.java
 * Purpose: Real Spring/Flyway/JDBC/HTTP query and bounded audit-review acceptance by Jason Stys.
 * Symbols: isolated synthetic accounts/jobs, cursor/page/history and viewer/invalid-input assertions.
 * Exact declarations and scoped variable locations: docs/code-index.md.
 */
package dev.jasonstys.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import dev.jasonstys.operations.domain.FailurePlan;
import dev.jasonstys.operations.connector.ConnectorRegistry;
import dev.jasonstys.operations.domain.JobStatus;
import dev.jasonstys.operations.domain.SyncJob;
import dev.jasonstys.operations.service.OperationsService;
import dev.jasonstys.operations.service.RetryPolicy;
import dev.jasonstys.operations.store.OperationsStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Native H2 PostgreSQL-compatible acceptance; the same class runs against actual PostgreSQL in CI. */
@SpringBootTest
@AutoConfigureMockMvc
class OperationsReviewIntegrationTest {
    @Autowired
    private OperationsService service;
    @Autowired
    private OperationsStore store;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ConnectorRegistry registry;
    @Autowired
    private RetryPolicy retryPolicy;

    private UUID account() {
        return service.createAccount("atlas-ads", "Review fixture", "review-" + UUID.randomUUID()).account().id();
    }

    private SyncJob submit(UUID account) {
        return service.submit(account, FailurePlan.NONE, 3, "review-fixture", UUID.randomUUID().toString(), "fixture").job();
    }

    @Test
    void filteredKeysetPagesBindQueryWithoutMissingUnchangedJobs() {
        UUID account = account();
        List<UUID> expected = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            expected.add(submit(account).id());
        }
        SyncJob cancelled = submit(account);
        service.cancel(cancelled.id(), "fixture");
        submit(account());
        List<UUID> found = new ArrayList<>();
        String cursor = null;
        do {
            OperationsStore.JobPage page = service.jobs(2, cursor, account, JobStatus.QUEUED);
            assertThat(page.items()).allMatch(job -> job.accountId().equals(account) && job.status() == JobStatus.QUEUED);
            found.addAll(page.items().stream().map(SyncJob::id).toList());
            cursor = page.nextCursor();
        } while (cursor != null);
        assertThat(found).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(new HashSet<>(found)).hasSize(5);
        String scoped = service.jobs(1, null, account, JobStatus.QUEUED).nextCursor();
        assertThatThrownBy(() -> service.jobs(1, scoped, account, JobStatus.CANCELLED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.jobs(1, scoped, UUID.randomUUID(), JobStatus.QUEUED)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.jobs(20, null, UUID.randomUUID(), null).items()).isEmpty();
        assertThatThrownBy(() -> store.listJobs(0, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.listJobs(101, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundedHistoryCoversEveryEventAndPreservesCurrentState() {
        UUID id = submit(account()).id();
        while (service.job(id).status() != JobStatus.SUCCEEDED) {
            service.process(id);
        }
        SyncJob original = service.job(id);
        List<Long> expected = service.audit(id).stream().map(event -> event.id()).toList();
        List<Long> found = new ArrayList<>();
        Long before = null;
        do {
            OperationsService.JobInspection review = service.inspection(id, 2, before);
            assertThat(review.job()).isEqualTo(original);
            assertThat(review.nextEligibleDelayMs()).isNull();
            assertThat(review.history().items()).hasSizeLessThanOrEqualTo(2);
            List<Long> ids = review.history().items().stream().map(event -> event.id()).toList();
            assertThat(ids).isSorted();
            found.addAll(ids);
            before = review.history().nextBefore() == null ? null : Long.valueOf(review.history().nextBefore());
        } while (before != null);
        assertThat(found).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(new HashSet<>(found)).hasSize(expected.size());
        assertThat(service.job(id)).isEqualTo(original);
        assertThat(service.inspection(id, 2, 1L).history().items()).isEmpty();
        assertThatThrownBy(() -> store.auditPage(id, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.auditPage(id, 101, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.auditPage(id, 1, 0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void viewersReadFilteredEvidenceAndInvalidQueriesFailSafely() throws Exception {
        UUID account = account();
        SyncJob job = submit(account);
        mvc.perform(get("/api/v1/jobs").queryParam("accountId", account.toString()).queryParam("status", "QUEUED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(job.id().toString()));
        mvc.perform(get("/api/v1/jobs/{id}/inspection", job.id()).queryParam("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.job.id").value(job.id().toString()))
                .andExpect(jsonPath("$.nextEligibleDelayMs").value(0))
                .andExpect(jsonPath("$.history.items[0].eventType").value("JOB_SUBMITTED"));
        mvc.perform(get("/api/v1/jobs/{id}/inspection", job.id()).queryParam("before", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/jobs/{id}/inspection", job.id()).queryParam("limit", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/jobs/{id}/inspection", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/jobs").queryParam("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/jobs").queryParam("accountId", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/jobs").queryParam("cursor", "x".repeat(257))).andExpect(status().isBadRequest());
    }

    @Test
    void retryDelayAndDeadLetterStateRemainExplicit() {
        UUID account = account();
        SyncJob delayed = service.submit(account, FailurePlan.RATE_LIMIT_ONCE, 3, "fixture", UUID.randomUUID().toString(), "fixture").job();
        service.process(delayed.id());
        OperationsService.JobInspection review = service.inspection(delayed.id(), 25, null);
        assertThat(review.job().status()).isEqualTo(JobStatus.RETRY_WAIT);
        assertThat(review.nextEligibleDelayMs()).isBetween(0L, 30_000L);
        SyncJob failed = service.submit(account, FailurePlan.PERMANENT, 3, "fixture", UUID.randomUUID().toString(), "fixture").job();
        service.process(failed.id());
        assertThat(service.inspection(failed.id(), 25, null).nextEligibleDelayMs()).isNull();
    }

    @Test
    void idempotencyKeyCannotSilentlyChangeAccountOrSyncPolicy() {
        UUID account = account();
        String key = UUID.randomUUID().toString();
        SyncJob original = service.submit(account, FailurePlan.NONE, 3, "first", key, "fixture").job();
        assertThat(service.submit(account, FailurePlan.NONE, 3, "retry", key, "fixture").job()).isEqualTo(original);
        UUID other = account();
        assertThatThrownBy(() -> service.submit(other, FailurePlan.NONE, 3, "fixture", key, "fixture")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.submit(account, FailurePlan.PERMANENT, 3, "fixture", key, "fixture")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.submit(account, FailurePlan.NONE, 2, "fixture", key, "fixture")).isInstanceOf(IllegalStateException.class);
        assertThat(service.job(original.id())).isEqualTo(original);
    }

    @Test
    void nanosecondClockCannotRoundAnImmediateJobIntoTheFuture() {
        Instant nanos = Instant.parse("2026-11-09T12:00:00.123456789Z");
        OperationsService fixed = new OperationsService(store, registry, retryPolicy,
                Clock.fixed(nanos, ZoneOffset.UTC), new SimpleMeterRegistry());
        UUID account = fixed.createAccount("atlas-ads", "Microsecond boundary", "micro-" + UUID.randomUUID()).account().id();
        String key = UUID.randomUUID().toString();
        SyncJob first = fixed.submit(account, FailurePlan.NONE, 3, "fixture", key, "fixture").job();
        assertThat(first.createdAt()).isEqualTo(Instant.parse("2026-11-09T12:00:00.123456Z"));
        assertThat(fixed.job(first.id())).isEqualTo(first);
        assertThat(fixed.submit(account, FailurePlan.NONE, 3, "retry", key, "fixture").job()).isEqualTo(first);
        assertThat(fixed.process(first.id()).status()).isEqualTo(JobStatus.QUEUED);
        assertThat(fixed.process(first.id()).status()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void requiredNullableReviewFieldsArePresentOnTheActualWire() throws Exception {
        UUID account = account();
        SyncJob job = submit(account);
        service.cancel(job.id(), "fixture");
        mvc.perform(get("/api/v1/jobs").queryParam("accountId", account.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        mvc.perform(get("/api/v1/jobs/{id}/inspection", job.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nextEligibleDelayMs").hasJsonPath())
                .andExpect(jsonPath("$.nextEligibleDelayMs").value(nullValue()))
                .andExpect(jsonPath("$.history.nextBefore").hasJsonPath())
                .andExpect(jsonPath("$.history.nextBefore").value(nullValue()));
    }
}
