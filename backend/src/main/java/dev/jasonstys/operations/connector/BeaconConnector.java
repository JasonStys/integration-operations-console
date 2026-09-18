/*
 * File: BeaconConnector.java
 * Purpose: Three-page deterministic fictional audience-data connector.
 * Symbols: connector metadata and fetchPage(); exact lines are indexed in docs/code-index.md.
 * Variables: cursor selects one of three pages; attempt/failurePlan drive failure injection.
 */
package dev.jasonstys.operations.connector;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.jasonstys.operations.domain.FailurePlan;

/** Fake schema-distinct connector used to demonstrate a shared integration port. */
@Component
public final class BeaconConnector extends AbstractFakeConnector {
    @Override
    public String id() {
        return "beacon-audience";
    }

    @Override
    public String displayName() {
        return "Beacon Audience (simulated)";
    }

    @Override
    public ProviderPage fetchPage(
            String externalReference,
            String cursor,
            int attempt,
            FailurePlan failurePlan) throws ConnectorException {
        enforcePolicy(attempt, failurePlan);
        if (cursor == null) {
            return new ProviderPage(List.of(record(externalReference, "segment-a")), "beacon:2");
        }
        if ("beacon:2".equals(cursor)) {
            return new ProviderPage(List.of(record(externalReference, "segment-b")), "beacon:3");
        }
        if ("beacon:3".equals(cursor)) {
            return new ProviderPage(List.of(record(externalReference, "segment-c")), null);
        }
        throw new ConnectorException.Permanent("unknown Beacon page cursor");
    }
}
