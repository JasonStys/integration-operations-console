/*
 * File: OperationsController.java
 * Purpose: Versioned REST boundary for connectors, accounts, jobs, audit, and dashboard data.
 * Symbols: endpoint methods, service field, and role/actor helpers; exact lines are in docs/code-index.md.
 * Variables: page limits are bounded, idempotency keys are validated, and role headers are demo-only.
 */
package dev.jasonstys.operations.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jasonstys.operations.connector.ConnectorRegistry;
import dev.jasonstys.operations.domain.AuditEvent;
import dev.jasonstys.operations.domain.ConnectorAccount;
import dev.jasonstys.operations.domain.JobStatus;
import dev.jasonstys.operations.domain.SyncJob;
import dev.jasonstys.operations.service.OperationsService;
import dev.jasonstys.operations.store.OperationsStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Thin HTTP adapter; business transitions are delegated to OperationsService. */
@Validated
@RestController
@RequestMapping("/api/v1")
public class OperationsController {
    private final OperationsService service;

    /** @param service application use cases */
    public OperationsController(OperationsService service) {
        this.service = service;
    }

    /** @return available fake providers */
    @GetMapping("/connectors")
    public List<ConnectorRegistry.Descriptor> connectors() {
        return service.connectors();
    }

    /** Creates or deduplicates a fake provider account. */
    @PostMapping("/accounts")
    public ResponseEntity<ApiModels.MutationResult<ConnectorAccount>> createAccount(
            @Valid @RequestBody ApiModels.CreateAccountRequest body,
            @RequestHeader(name = "X-Demo-Role", defaultValue = "viewer") String role) {
        requireOperator(role);
        OperationsStore.AccountResult result = service.createAccount(
                body.connectorId(), body.displayName(), body.externalReference());
        ApiModels.MutationResult<ConnectorAccount> response =
                new ApiModels.MutationResult<>(result.account(), result.created());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/accounts/" + result.account().id()))
                    .body(response);
        }
        return ResponseEntity.ok(response);
    }

    /** @return all linked fake accounts */
    @GetMapping("/accounts")
    public List<ConnectorAccount> accounts() {
        return service.accounts();
    }

    /** Creates or deduplicates a synchronization job. */
    @PostMapping("/jobs")
    public ResponseEntity<ApiModels.MutationResult<SyncJob>> submit(
            @Valid @RequestBody ApiModels.SubmitJobRequest body,
            @RequestHeader(name = "Idempotency-Key")
            @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String idempotencyKey,
            @RequestHeader(name = "X-Demo-Role", defaultValue = "viewer") String role,
            @RequestHeader(name = "X-Demo-Actor", required = false) @Size(max = 100) String actor,
            HttpServletRequest request) {
        requireOperator(role);
        OperationsStore.SubmissionResult result = service.submit(
                body.accountId(), body.failurePlan(), body.maxAttempts(),
                correlationId(request), idempotencyKey, actor(actor));
        ApiModels.MutationResult<SyncJob> response =
                new ApiModels.MutationResult<>(result.job(), result.created());
        if (result.created()) {
            return ResponseEntity.accepted()
                    .location(URI.create("/api/v1/jobs/" + result.job().id()))
                    .body(response);
        }
        return ResponseEntity.ok(response);
    }

    /** @return stable keyset-paginated job page */
    @GetMapping("/jobs")
    public OperationsStore.JobPage jobs(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor) {
        return service.jobs(limit, cursor);
    }

    /** @return a single job */
    @GetMapping("/jobs/{id}")
    public SyncJob job(@PathVariable UUID id) {
        return service.job(id);
    }

    /** Runs a named eligible job for deterministic demonstrations. */
    @PostMapping("/jobs/{id}/run")
    public SyncJob run(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Demo-Role", defaultValue = "viewer") String role) {
        requireOperator(role);
        return service.process(id);
    }

    /** Runs the earliest eligible job, if one exists. */
    @PostMapping("/jobs/run-next")
    public ApiModels.RunNextResult runNext(
            @RequestHeader(name = "X-Demo-Role", defaultValue = "viewer") String role) {
        requireOperator(role);
        return service.processNext()
                .map(job -> new ApiModels.RunNextResult(job, true))
                .orElseGet(() -> new ApiModels.RunNextResult(null, false));
    }

    /** Cancels non-terminal work. */
    @PostMapping("/jobs/{id}/cancel")
    public SyncJob cancel(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Demo-Role", defaultValue = "viewer") String role,
            @RequestHeader(name = "X-Demo-Actor", required = false) @Size(max = 100) String actor) {
        requireOperator(role);
        return service.cancel(id, actor(actor));
    }

    /** Replays one dead-lettered job. */
    @PostMapping("/jobs/{id}/replay")
    public SyncJob replay(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Demo-Role", defaultValue = "viewer") String role,
            @RequestHeader(name = "X-Demo-Actor", required = false) @Size(max = 100) String actor) {
        requireOperator(role);
        return service.replay(id, actor(actor));
    }

    /** @return append-only lifecycle history */
    @GetMapping("/jobs/{id}/audit")
    public List<AuditEvent> audit(@PathVariable UUID id) {
        return service.audit(id);
    }

    /** @return dashboard counts and ten most recently updated jobs */
    @GetMapping("/dashboard")
    public ApiModels.Dashboard dashboard() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (JobStatus status : JobStatus.values()) {
            counts.put(status.name(), 0L);
        }
        service.statusCounts().forEach(item -> counts.put(item.status(), item.count()));
        return new ApiModels.Dashboard(counts, service.jobs(10, null).items());
    }

    private static void requireOperator(String role) {
        if (!"operator".equalsIgnoreCase(role)) {
            throw new ForbiddenOperationException(
                    "Set X-Demo-Role: operator for state-changing demo requests");
        }
    }

    private static String actor(String supplied) {
        if (supplied == null || supplied.isBlank()) {
            return "demo-operator";
        }
        return supplied.trim();
    }

    private static String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
    }
}
