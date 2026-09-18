/*
 * File: ProviderPage.java
 * Purpose: Carries one cursor-paginated provider response.
 * Symbols: records and nextCursor components; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.connector;

import java.util.List;

/**
 * Immutable page from a connector.
 *
 * @param records normalized records in provider order
 * @param nextCursor opaque continuation value, or null when complete
 */
public record ProviderPage(List<ProviderRecord> records, String nextCursor) {
    /** Prevents callers from mutating connector output. */
    public ProviderPage {
        records = List.copyOf(records);
    }
}
