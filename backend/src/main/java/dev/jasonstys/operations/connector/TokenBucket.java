/*
 * File: TokenBucket.java
 * Purpose: Thread-safe O(1) client-side rate limiter for connector calls.
 * Symbols: capacity/refillPerSecond configuration and mutable tokens/lastRefillNanos state;
 * exact lines are indexed in docs/code-index.md.
 */
package dev.jasonstys.operations.connector;

/**
 * Token-bucket limiter with constant-time refill and consume operations.
 * Time is supplied by the caller, enabling deterministic tests.
 */
public final class TokenBucket {
    private final double capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastRefillNanos;

    /**
     * Creates a full bucket.
     *
     * @param capacity maximum tokens, greater than zero
     * @param refillPerSecond token replenishment rate, greater than zero
     * @param initialNanos monotonic origin
     */
    public TokenBucket(double capacity, double refillPerSecond, long initialNanos) {
        if (capacity <= 0 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refill rate must be positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = initialNanos;
    }

    /**
     * Tries to consume tokens after applying the elapsed-time refill.
     * Complexity is O(1) time and O(1) space.
     *
     * @param requested positive number of tokens
     * @param nowNanos monotonic current time
     * @return true when tokens were consumed
     */
    public synchronized boolean tryConsume(double requested, long nowNanos) {
        if (requested <= 0 || requested > capacity) {
            throw new IllegalArgumentException("requested tokens are outside bucket capacity");
        }
        if (nowNanos < lastRefillNanos) {
            throw new IllegalArgumentException("monotonic time cannot move backwards");
        }
        double elapsedSeconds = (nowNanos - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        lastRefillNanos = nowNanos;
        if (tokens < requested) {
            return false;
        }
        tokens -= requested;
        return true;
    }
}
