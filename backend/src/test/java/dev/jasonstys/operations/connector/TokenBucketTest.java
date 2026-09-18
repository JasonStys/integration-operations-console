/*
 * File: TokenBucketTest.java
 * Purpose: Verifies quota consumption, refill, validation, and monotonic-time safety.
 * Symbols: independent token bucket scenarios; exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Deterministic tests supply nanosecond timestamps directly. */
class TokenBucketTest {
    @Test
    void consumesThenRefillsInConstantTime() {
        TokenBucket bucket = new TokenBucket(2.0, 1.0, 0L);

        assertThat(bucket.tryConsume(1.0, 0L)).isTrue();
        assertThat(bucket.tryConsume(1.0, 0L)).isTrue();
        assertThat(bucket.tryConsume(1.0, 0L)).isFalse();
        assertThat(bucket.tryConsume(1.0, 1_000_000_000L)).isTrue();
    }

    @Test
    void rejectsInvalidConfigurationAndClockRegression() {
        assertThatThrownBy(() -> new TokenBucket(0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        TokenBucket bucket = new TokenBucket(2, 1, 10);
        assertThatThrownBy(() -> bucket.tryConsume(1, 9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucket.tryConsume(3, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
