/*
 * File: AbstractFakeConnector.java
 * Purpose: Shares deterministic failure injection and token-bucket enforcement.
 * Symbols: limiter field, enforcePolicy(), and record() helper; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.connector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import dev.jasonstys.operations.domain.FailurePlan;

/** Common behavior for the two fictional connectors. */
abstract class AbstractFakeConnector implements SimulatedConnector {
    private final TokenBucket limiter = new TokenBucket(100.0, 50.0, System.nanoTime());

    /**
     * Applies deterministic rate-limit, transient, permanent, and local quota behavior.
     *
     * @param attempt number of prior failures
     * @param plan requested simulation plan
     * @throws ConnectorException when the plan requires a failure
     */
    protected final void enforcePolicy(int attempt, FailurePlan plan) throws ConnectorException {
        if (!limiter.tryConsume(1.0, System.nanoTime())) {
            throw new ConnectorException.RateLimited("client token bucket exhausted", Duration.ofSeconds(1));
        }
        if (plan == FailurePlan.RATE_LIMIT_ONCE && attempt == 0) {
            throw new ConnectorException.RateLimited("provider returned a simulated 429", Duration.ofSeconds(5));
        }
        if (plan == FailurePlan.TRANSIENT_TWICE && attempt < 2) {
            throw new ConnectorException.Transient("provider returned a simulated 503");
        }
        if (plan == FailurePlan.PERMANENT) {
            throw new ConnectorException.Permanent("provider schema is intentionally incompatible");
        }
    }

    /**
     * Creates stable fake normalized data without exposing any real records.
     *
     * @param externalReference fake account identifier
     * @param item provider-local item name
     * @return normalized record with SHA-256 content fingerprint
     */
    protected final ProviderRecord record(String externalReference, String item) {
        String source = id() + ':' + externalReference + ':' + item;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return new ProviderRecord(item, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
