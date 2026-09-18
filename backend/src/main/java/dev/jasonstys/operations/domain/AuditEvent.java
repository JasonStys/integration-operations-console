/*
 * File: AuditEvent.java
 * Purpose: Immutable append-only explanation of an operator or scheduler action.
 * Symbols: AuditEvent record components; exact lines are indexed in docs/code-index.md.
 * State: id may be null before insertion; all other fields are required.
 */
package dev.jasonstys.operations.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record returned in chronological order.
 *
 * @param id database sequence value, or null before persistence
 * @param jobId affected job
 * @param eventType stable event name
 * @param actor scheduler or user identifier
 * @param detail concise sanitized explanation
 * @param occurredAt event timestamp
 */
public record AuditEvent(
        Long id,
        UUID jobId,
        String eventType,
        String actor,
        String detail,
        Instant occurredAt) {
}
