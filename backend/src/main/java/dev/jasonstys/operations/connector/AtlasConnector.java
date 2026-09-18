/*
 * File: AtlasConnector.java
 * Purpose: Two-page deterministic fictional advertising-data connector.
 * Symbols: connector metadata and fetchPage(); exact lines are indexed in docs/code-index.md.
 * Variables: cursor selects page zero or one; attempt/failurePlan drive failure injection.
 */
package dev.jasonstys.operations.connector;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.jasonstys.operations.domain.FailurePlan;

/** Fake paginated connector with no network or credential dependency. */
@Component
public final class AtlasConnector extends AbstractFakeConnector {
    @Override
    public String id() {
        return "atlas-ads";
    }

    @Override
    public String displayName() {
        return "Atlas Ads (simulated)";
    }

    @Override
    public ProviderPage fetchPage(
            String externalReference,
            String cursor,
            int attempt,
            FailurePlan failurePlan) throws ConnectorException {
        enforcePolicy(attempt, failurePlan);
        if (cursor == null) {
            return new ProviderPage(List.of(
                    record(externalReference, "campaign-100"),
                    record(externalReference, "campaign-101"),
                    record(externalReference, "campaign-102")), "atlas-page-2");
        }
        if ("atlas-page-2".equals(cursor)) {
            return new ProviderPage(List.of(
                    record(externalReference, "campaign-103"),
                    record(externalReference, "campaign-104")), null);
        }
        throw new ConnectorException.Permanent("unknown Atlas page cursor");
    }
}
