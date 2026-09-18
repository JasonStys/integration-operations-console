/*
 * File: ApiModels.java
 * Purpose: Validated HTTP request and small response contracts.
 * Symbols: account/job request records, mutation result, dashboard, and empty-queue response;
 * exact lines are generated in docs/code-index.md.
 */
package dev.jasonstys.operations.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.jasonstys.operations.domain.FailurePlan;
import dev.jasonstys.operations.domain.SyncJob;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Namespace for immutable API-only data transfer records. */
public final class ApiModels {
    private ApiModels() {
    }

    /** Validated fake account-link request. */
    public record CreateAccountRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{2,40}") String connectorId,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{2,120}") String externalReference) {
    }

    /** Validated synchronization request. */
    public record SubmitJobRequest(
            @NotNull UUID accountId,
            @NotNull FailurePlan failurePlan,
            @NotNull @Min(1) @Max(10) Integer maxAttempts) {
    }

    /** Indicates whether an idempotent creation actually inserted a resource. */
    public record MutationResult<T>(T resource, boolean created) {
    }

    /** Scheduler response when no work is currently eligible. */
    public record RunNextResult(SyncJob job, boolean workAvailable) {
    }

    /** Operational summary used by the dashboard. */
    public record Dashboard(Map<String, Long> statusCounts, List<SyncJob> recentJobs) {
        public Dashboard {
            statusCounts = Map.copyOf(statusCounts);
            recentJobs = List.copyOf(recentJobs);
        }
    }
}
