/*
 * File: PerformanceCharacterizationTest.java
 * Purpose: Generous regression ceiling for O(1) retry and token-bucket hot-path operations.
 * Symbols: deterministic high-volume characterization; exact lines are in docs/code-index.md.
 * Note: This is a CI regression signal, not a substitute for JMH microbenchmarking.
 */
package dev.jasonstys.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.jasonstys.operations.connector.TokenBucket;
import dev.jasonstys.operations.service.RetryPolicy;

/** Characterizes 250,000 policy operations under an intentionally loose CI budget. */
@Tag("performance")
class PerformanceCharacterizationTest {
    @Test
    void constantTimePoliciesStayWithinRegressionBudget() {
        RetryPolicy retryPolicy = new RetryPolicy();
        TokenBucket bucket = new TokenBucket(300_000, 1, 0);
        UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        long started = System.nanoTime();

        for (int index = 0; index < 250_000; index++) {
            retryPolicy.delay(jobId, index % 10 + 1);
            if (!bucket.tryConsume(1, index)) {
                throw new AssertionError("pre-sized token bucket unexpectedly exhausted");
            }
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
        System.out.printf("PERFORMANCE policy_operations=250000 elapsed_ms=%d%n", elapsed.toMillis());
    }
}
