/*
 * File: ConnectorAccount.java
 * Purpose: Immutable description of one simulated external account link.
 * Symbols: ConnectorAccount record and its id, connectorId, displayName,
 * externalReference, and createdAt components; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a safe fake-provider account; it never contains tokens or secrets.
 *
 * @param id internal identifier
 * @param connectorId connector implementation key
 * @param displayName operator-facing account label
 * @param externalReference non-secret provider-side reference
 * @param createdAt creation timestamp
 */
public record ConnectorAccount(
        UUID id,
        String connectorId,
        String displayName,
        String externalReference,
        Instant createdAt) {
}
