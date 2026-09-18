/*
 * File: ProviderRecord.java
 * Purpose: Minimal immutable item returned by a simulated connector.
 * Symbols: externalId and payloadHash record components; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.connector;

/**
 * Represents one non-sensitive provider record.
 *
 * @param externalId stable fake provider identifier
 * @param payloadHash deterministic stand-in for normalized content
 */
public record ProviderRecord(String externalId, String payloadHash) {
}
