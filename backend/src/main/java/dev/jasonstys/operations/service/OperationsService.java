/*
 * File: OperationsService.java
 * Purpose: Transactional account, scheduling, retry, cancellation, replay, and audit orchestration.
 * Symbols: store/registry/retryPolicy/clock/metrics fields and public use-case methods;
 * exact lines are generated in docs/code-index.md.
 * Concurrency: process methods are synchronized for single-instance claim safety; the documented
 * multi-instance scale path replaces this with SELECT ... FOR UPDATE SKIP LOCKED.
 */
package dev.jasonstys.operations.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jasonstys.operations.connector.ConnectorException;
import dev.jasonstys.operations.connector.ConnectorRegistry;
import dev.jasonstys.operations.connector.ProviderPage;
import dev.jasonstys.operations.connector.SimulatedConnector;
import dev.jasonstys.operations.domain.AuditEvent;
import dev.jasonstys.operations.domain.ConnectorAccount;
import dev.jasonstys.operations.domain.FailurePlan;
import dev.jasonstys.operations.domain.JobStatus;
import dev.jasonstys.operations.domain.SyncJob;
import dev.jasonstys.operations.store.OperationsStore;
import dev.jasonstys.operations.store.PageCursor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Application service that keeps business transitions out of HTTP and SQL adapters. */
@Service
public class OperationsService {
    private final OperationsStore store;
    private final ConnectorRegistry registry;
    private final RetryPolicy retryPolicy;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    /**
     * @param store durable persistence port
     * @param registry connector lookup
     * @param retryPolicy deterministic backoff policy
     * @param clock injectable UTC time
     * @param meterRegistry tagged operational metrics
     */
    public OperationsService(
            OperationsStore store,
            ConnectorRegistry registry,
            RetryPolicy retryPolicy,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.store = store;
        this.registry = registry;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    /** Creates or deduplicates a fake account link. */
    @Transactional
    public OperationsStore.AccountResult createAccount(
            String connectorId,
            String displayName,
            String externalReference) {
        registry.require(connectorId);
        Instant now = clock.instant();
        ConnectorAccount account = new ConnectorAccount(
                UUID.randomUUID(), connectorId, displayName, externalReference, now);
        return store.saveAccountIfAbsent(account);
    }

    /** @return all linked demo accounts */
    @Transactional(readOnly = true)
    public List<ConnectorAccount> accounts() {
        return store.listAccounts();
    }

    /** Creates or deduplicates one sync job and writes its initial audit record atomically. */
    @Transactional
    public OperationsStore.SubmissionResult submit(
            UUID accountId,
            FailurePlan failurePlan,
            int maxAttempts,
            String correlationId,
            String idempotencyKey,
            String actor) {
        requireAccount(accountId);
        Instant now = clock.instant();
        SyncJob candidate = SyncJob.queued(
                UUID.randomUUID(), accountId, failurePlan, maxAttempts,
                correlationId, idempotencyKey, now);
        OperationsStore.SubmissionResult result = store.saveJobIfAbsent(candidate);
        if (result.created()) {
            audit(result.job(), "JOB_SUBMITTED", actor,
                    "Queued with failure plan " + failurePlan, now);
            counter("submitted", result.job().failurePlan()).increment();
        } else {
            counter("deduplicated", result.job().failurePlan()).increment();
        }
        return result;
    }

    /**
     * Claims and processes the earliest eligible job for this application instance.
     *
     * @return processed job, or empty result when no job is ready
     */
    @Transactional
    public synchronized java.util.Optional<SyncJob> processNext() {
        Instant now = clock.instant();
        return store.findNextReady(now).map(job -> process(job, now));
    }

    /** Processes a named job only when the scheduler would consider it ready. */
    @Transactional
    public synchronized SyncJob process(UUID id) {
        Instant now = clock.instant();
        SyncJob job = requireJob(id);
        if (job.status() != JobStatus.QUEUED && job.status() != JobStatus.RETRY_WAIT) {
            throw new IllegalStateException("job is not runnable from " + job.status());
        }
        if (job.availableAt().isAfter(now)) {
            throw new IllegalStateException("job is delayed until " + job.availableAt());
        }
        return process(job, now);
    }

    /** Moves non-terminal work to CANCELLED and records who initiated the action. */
    @Transactional
    public SyncJob cancel(UUID id, String actor) {
        Instant now = clock.instant();
        SyncJob cancelled = requireJob(id).cancel(now);
        store.updateJob(cancelled);
        audit(cancelled, "JOB_CANCELLED", actor, "Operator cancelled pending work", now);
        counter("cancelled", cancelled.failurePlan()).increment();
        return cancelled;
    }

    /** Explicitly resets a dead-lettered job while preserving its cursor and processed count. */
    @Transactional
    public SyncJob replay(UUID id, String actor) {
        Instant now = clock.instant();
        SyncJob replayed = requireJob(id).replay(now);
        store.updateJob(replayed);
        audit(replayed, "JOB_REPLAYED", actor, "Operator replayed dead-lettered work", now);
        counter("replayed", replayed.failurePlan()).increment();
        return replayed;
    }

    /** @return job or a not-found exception */
    @Transactional(readOnly = true)
    public SyncJob job(UUID id) {
        return requireJob(id);
    }

    /** @return validated keyset page */
    @Transactional(readOnly = true)
    public OperationsStore.JobPage jobs(int limit, String encodedCursor) {
        PageCursor cursor = encodedCursor == null ? null : PageCursor.decode(encodedCursor);
        return store.listJobs(limit, cursor);
    }

    /** @return append-only audit history */
    @Transactional(readOnly = true)
    public List<AuditEvent> audit(UUID id) {
        requireJob(id);
        return store.listAudit(id);
    }

    /** @return current lifecycle counts */
    @Transactional(readOnly = true)
    public List<OperationsStore.StatusCount> statusCounts() {
        return store.countByStatus();
    }

    /** @return connector choices for account-link creation */
    public List<ConnectorRegistry.Descriptor> connectors() {
        return registry.descriptors();
    }

    private SyncJob process(SyncJob job, Instant now) {
        SyncJob running = job.start(now);
        store.updateJob(running);
        audit(running, "JOB_STARTED", "scheduler", "Fetching one provider page", now);

        ConnectorAccount account = requireAccount(running.accountId());
        SimulatedConnector connector = registry.require(account.connectorId());
        try {
            ProviderPage page = connector.fetchPage(
                    account.externalReference(), running.pageCursor(),
                    running.attempt(), running.failurePlan());
            SyncJob completedPage = page.nextCursor() == null
                    ? running.succeed(page.records().size(), now)
                    : running.continueWith(page.nextCursor(), page.records().size(), now);
            store.updateJob(completedPage);
            String eventType = completedPage.status() == JobStatus.SUCCEEDED
                    ? "JOB_SUCCEEDED" : "PAGE_ACCEPTED";
            audit(completedPage, eventType, "scheduler",
                    "Accepted " + page.records().size() + " normalized records", now);
            counter(completedPage.status().name().toLowerCase(), completedPage.failurePlan()).increment();
            return completedPage;
        } catch (ConnectorException.RateLimited exception) {
            Duration policyDelay = retryPolicy.delay(running.id(), running.attempt() + 1);
            Duration delay = policyDelay.compareTo(exception.retryAfter()) >= 0
                    ? policyDelay : exception.retryAfter();
            return handleRetryable(running, exception.getMessage(), delay, now);
        } catch (ConnectorException.Transient exception) {
            Duration delay = retryPolicy.delay(running.id(), running.attempt() + 1);
            return handleRetryable(running, exception.getMessage(), delay, now);
        } catch (ConnectorException.Permanent exception) {
            return deadLetter(running, exception.getMessage(), now);
        } catch (ConnectorException exception) {
            return deadLetter(running, "unclassified connector failure", now);
        }
    }

    private SyncJob handleRetryable(SyncJob running, String error, Duration delay, Instant now) {
        if (running.nextFailureExhaustsAttempts()) {
            return deadLetter(running, error, now);
        }
        SyncJob waiting = running.retry(error, now.plus(delay), now);
        store.updateJob(waiting);
        audit(waiting, "RETRY_SCHEDULED", "scheduler",
                error + "; next attempt at " + waiting.availableAt(), now);
        counter("retry_scheduled", waiting.failurePlan()).increment();
        return waiting;
    }

    private SyncJob deadLetter(SyncJob running, String error, Instant now) {
        SyncJob failed = running.deadLetter(error, now);
        store.updateJob(failed);
        audit(failed, "JOB_DEAD_LETTERED", "scheduler", error, now);
        counter("dead_lettered", failed.failurePlan()).increment();
        return failed;
    }

    private ConnectorAccount requireAccount(UUID id) {
        return store.findAccount(id)
                .orElseThrow(() -> new NoSuchElementException("account not found: " + id));
    }

    private SyncJob requireJob(UUID id) {
        return store.findJob(id)
                .orElseThrow(() -> new NoSuchElementException("job not found: " + id));
    }

    private void audit(SyncJob job, String eventType, String actor, String detail, Instant now) {
        store.appendAudit(new AuditEvent(null, job.id(), eventType, actor, detail, now));
    }

    private Counter counter(String outcome, FailurePlan plan) {
        return Counter.builder("operations.jobs")
                .description("Integration job lifecycle outcomes")
                .tag("outcome", outcome)
                .tag("failure_plan", plan.name().toLowerCase())
                .register(meterRegistry);
    }
}
