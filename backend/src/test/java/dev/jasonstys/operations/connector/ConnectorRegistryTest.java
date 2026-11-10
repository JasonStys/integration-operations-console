/*
 * File: ConnectorRegistryTest.java
 * Purpose: Checks discovery order, duplicate rejection, lookup identity, and immutable metadata.
 * Symbols: permutation oracle and metadata-only fixtures; docs/code-index.md has exact locations.
 * Variables: input permutations vary discovery order; expected metadata is independently specified.
 */
package dev.jasonstys.operations.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jasonstys.operations.domain.FailurePlan;
import org.junit.jupiter.api.Test;

/** No Spring, network, provider execution, hash-seed control, or implementation reflection. */
class ConnectorRegistryTest {
    @Test
    void discoveryUsesIdOrderAcrossEveryInputPermutationAndCollidingIds() {
        List<SimulatedConnector> discovered = new ArrayList<>(List.of(
                new MetadataConnector("zulu", "Zulu display"),
                new MetadataConnector("BB", "Collision B display"),
                new MetadataConnector("alpha", "Alpha display"),
                new MetadataConnector("Aa", "Collision A display"),
                new MetadataConnector("mike", "Mike display")));

        // These different IDs also exercise hash collision handling; expected order is explicit.
        assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());
        assertThat(verifyPermutations(discovered, 0)).isEqualTo(120);
    }

    @Test
    void rejectsDuplicateIdsInsteadOfReplacingAnImplementation() {
        SimulatedConnector first = new MetadataConnector("same-id", "First");
        SimulatedConnector second = new MetadataConnector("same-id", "Different implementation");

        assertThatThrownBy(() -> new ConnectorRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("duplicate connector id: same-id");
    }

    @Test
    void metadataAndLookupDoNotChangeWhenInputOrReturnedListIsMutated() {
        SimulatedConnector first = new MetadataConnector("alpha", "Alpha");
        SimulatedConnector second = new MetadataConnector("zulu", "Zulu");
        List<SimulatedConnector> discovered = new ArrayList<>(List.of(second, first));
        ConnectorRegistry registry = new ConnectorRegistry(discovered);
        List<ConnectorRegistry.Descriptor> metadata = registry.descriptors();
        discovered.clear();
        discovered.add(new MetadataConnector("new-id", "Not registered"));

        assertThatThrownBy(metadata::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> metadata.set(0, new ConnectorRegistry.Descriptor("other", "Other")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.descriptors()).containsExactly(
                new ConnectorRegistry.Descriptor("alpha", "Alpha"),
                new ConnectorRegistry.Descriptor("zulu", "Zulu"));
        assertThat(registry.require("alpha")).isSameAs(first);
        assertThat(registry.require("zulu")).isSameAs(second);
        assertThatThrownBy(() -> registry.require("new-id"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown connector: new-id");
    }

    @Test
    void emptyDiscoveryHasNoMetadataAndRejectsUnknownLookup() {
        ConnectorRegistry registry = new ConnectorRegistry(List.of());

        assertThat(registry.descriptors()).isEmpty();
        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown connector: missing");
    }

    /** Exercise all five-factorial discovery orders against hand-written ID/display pairs. */
    private int verifyPermutations(List<SimulatedConnector> discovered, int offset) {
        if (offset == discovered.size()) {
            ConnectorRegistry registry = new ConnectorRegistry(discovered);
            assertThat(registry.descriptors()).containsExactly(
                    new ConnectorRegistry.Descriptor("Aa", "Collision A display"),
                    new ConnectorRegistry.Descriptor("BB", "Collision B display"),
                    new ConnectorRegistry.Descriptor("alpha", "Alpha display"),
                    new ConnectorRegistry.Descriptor("mike", "Mike display"),
                    new ConnectorRegistry.Descriptor("zulu", "Zulu display"));
            for (SimulatedConnector connector : discovered) {
                assertThat(registry.require(connector.id())).isSameAs(connector);
            }
            return 1;
        }
        int checked = 0;
        for (int index = offset; index < discovered.size(); index++) {
            Collections.swap(discovered, offset, index);
            checked += verifyPermutations(discovered, offset + 1);
            Collections.swap(discovered, offset, index);
        }
        return checked;
    }

    /** Immutable metadata fixture whose provider method must never be used by discovery. */
    private record MetadataConnector(String id, String displayName) implements SimulatedConnector {
        @Override
        public ProviderPage fetchPage(String reference, String cursor, int attempt, FailurePlan plan) {
            throw new UnsupportedOperationException("Metadata discovery must not fetch provider data");
        }
    }
}
