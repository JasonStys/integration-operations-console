/*
 * File: RetryPolicyTest.java
 * Purpose: Unit and property-style checks for capped exponential backoff and jitter.
 * Symbols: deterministic, monotonic, cap, and validation tests; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Validates retry properties across a useful attempt range. */
class RetryPolicyTest {
    private final RetryPolicy policy = new RetryPolicy();
    private final UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void isDeterministicAndNonDecreasing() {
        Duration previous = Duration.ZERO;
        for (int attempt = 1; attempt <= 10; attempt++) {
            Duration delay = policy.delay(jobId, attempt);
            assertThat(delay).isEqualTo(policy.delay(jobId, attempt));
            assertThat(delay).isGreaterThanOrEqualTo(previous);
            previous = delay;
        }
    }

    @Test
    void capsExponentialPortionAndBoundsJitter() {
        Duration delay = policy.delay(jobId, 30);

        assertThat(delay).isBetween(Duration.ofMinutes(1), Duration.ofSeconds(61));
    }

    @Test
    void rejectsZeroBasedFailureNumbers() {
        assertThatThrownBy(() -> policy.delay(jobId, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
