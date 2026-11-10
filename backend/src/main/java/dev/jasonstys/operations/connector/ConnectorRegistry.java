/*
 * File: ConnectorRegistry.java
 * Purpose: Validated O(1) connector lookup and stable API discovery ordering.
 * Symbols: connectorsById lookup, orderedDescriptors snapshot, require(), and descriptors().
 * Variables: exact declaration locations are generated in docs/code-index.md.
 */
package dev.jasonstys.operations.connector;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Registry prevents conditional logic from spreading through job orchestration. */
@Component
public final class ConnectorRegistry {
    private final Map<String, SimulatedConnector> connectorsById;
    private final List<Descriptor> orderedDescriptors;

    /**
     * Builds immutable lookup/metadata snapshots and rejects duplicate connector IDs.
     *
     * @param connectors discovered Spring connector implementations
     */
    public ConnectorRegistry(List<SimulatedConnector> connectors) {
        Map<String, SimulatedConnector> indexed = new LinkedHashMap<>();
        connectors.stream().sorted(Comparator.comparing(SimulatedConnector::id)).forEach(connector -> {
            if (indexed.put(connector.id(), connector) != null) {
                throw new IllegalStateException("duplicate connector id: " + connector.id());
            }
        });
        connectorsById = Map.copyOf(indexed);
        // Map.copyOf has no encounter-order contract; capture the explicitly sorted source instead.
        orderedDescriptors = indexed.values().stream()
                .map(connector -> new Descriptor(connector.id(), connector.displayName()))
                .toList();
    }

    /**
     * Finds a connector in expected O(1) hash-map time.
     *
     * @param id connector key
     * @return matching implementation
     */
    public SimulatedConnector require(String id) {
        SimulatedConnector connector = connectorsById.get(id);
        if (connector == null) {
            throw new IllegalArgumentException("unknown connector: " + id);
        }
        return connector;
    }

    /** @return immutable metadata in ascending, case-sensitive connector-ID order */
    public List<Descriptor> descriptors() {
        return orderedDescriptors;
    }

    /** API-safe connector metadata. */
    public record Descriptor(String id, String displayName) {
    }
}
