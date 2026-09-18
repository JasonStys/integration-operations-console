/*
 * File: ConnectorException.java
 * Purpose: Typed provider failures used by retry and dead-letter policy.
 * Symbols: ConnectorException plus RateLimited, Transient, and Permanent subclasses;
 * exact lines are indexed in docs/code-index.md.
 */
package dev.jasonstys.operations.connector;

import java.time.Duration;

/** Base checked failure from the simulated integration boundary. */
public abstract class ConnectorException extends Exception {
    protected ConnectorException(String message) {
        super(message);
    }

    /** Provider quota response with a server-suggested minimum delay. */
    public static final class RateLimited extends ConnectorException {
        private final Duration retryAfter;

        /**
         * @param message safe operator-facing detail
         * @param retryAfter minimum retry delay
         */
        public RateLimited(String message, Duration retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        /** @return provider-suggested delay */
        public Duration retryAfter() {
            return retryAfter;
        }
    }

    /** Temporary provider failure that may succeed later. */
    public static final class Transient extends ConnectorException {
        public Transient(String message) {
            super(message);
        }
    }

    /** Non-retryable provider or schema failure. */
    public static final class Permanent extends ConnectorException {
        public Permanent(String message) {
            super(message);
        }
    }
}
