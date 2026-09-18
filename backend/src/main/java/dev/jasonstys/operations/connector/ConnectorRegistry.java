/*
 * File: ConnectorRegistry.java
 * Purpose: Validated O(1) connector lookup and stable API discovery ordering.
 * Symbols: connectorsById map plus require() and descriptors(); exact lines are in docs/code-index.md.
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

    /**
     * Builds an immutable-by-convention lookup and rejects duplicate connector IDs.
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

    /** @return stable connector metadata for the UI */
    public List<Descriptor> descriptors() {
        return connectorsById.values().stream()
                .map(connector -> new Descriptor(connector.id(), connector.displayName()))
                .toList();
    }

    /** API-safe connector metadata. */
    public record Descriptor(String id, String displayName) {
    }
}
