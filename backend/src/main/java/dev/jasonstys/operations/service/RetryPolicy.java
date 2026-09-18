/*
 * File: RetryPolicy.java
 * Purpose: Deterministic capped exponential backoff with bounded jitter.
 * Symbols: baseDelay/maxDelay configuration and delay(); exact lines are in docs/code-index.md.
 * Complexity: O(1) time and O(1) space for every calculation.
 */
package dev.jasonstys.operations.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** Retry timing policy designed to prevent synchronized retry storms. */
@Component
public final class RetryPolicy {
    private static final Duration BASE_DELAY = Duration.ofSeconds(2);
    private static final Duration MAX_DELAY = Duration.ofMinutes(1);

    /**
     * Calculates base * 2^(failureNumber-1), capped, plus deterministic sub-second jitter.
     *
     * @param jobId stable jitter seed
     * @param failureNumber one-based failed attempt number
     * @return bounded retry delay
     */
    public Duration delay(UUID jobId, int failureNumber) {
        if (failureNumber < 1) {
            throw new IllegalArgumentException("failureNumber must be positive");
        }
        int exponent = Math.min(failureNumber - 1, 20);
        long exponentialSeconds = BASE_DELAY.toSeconds() * (1L << exponent);
        long cappedSeconds = Math.min(exponentialSeconds, MAX_DELAY.toSeconds());
        long jitterMillis = Math.floorMod(jobId.hashCode() * 31L + failureNumber * 17L, 1_000L);
        return Duration.ofSeconds(cappedSeconds).plusMillis(jitterMillis);
    }
}
