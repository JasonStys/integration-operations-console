/*
 * File: SimulatedConnector.java
 * Purpose: Port implemented by deterministic fake external systems.
 * Symbols: metadata accessors and fetchPage operation; exact lines are in docs/code-index.md.
 * Variables: method parameters carry the account reference, cursor, attempt, and failure plan.
 */
package dev.jasonstys.operations.connector;

import dev.jasonstys.operations.domain.FailurePlan;

/** External-system boundary used by the scheduler without vendor coupling. */
public interface SimulatedConnector {
    /** @return stable connector key persisted with linked accounts */
    String id();

    /** @return human-readable fictional provider name */
    String displayName();

    /**
     * Fetches one deterministic page.
     *
     * @param externalReference non-secret account reference
     * @param cursor opaque cursor, or null for the first page
     * @param attempt failed attempts already consumed
     * @param failurePlan configured demo failure
     * @return normalized page
     * @throws ConnectorException typed simulated provider failure
     */
    ProviderPage fetchPage(
            String externalReference,
            String cursor,
            int attempt,
            FailurePlan failurePlan) throws ConnectorException;
}
