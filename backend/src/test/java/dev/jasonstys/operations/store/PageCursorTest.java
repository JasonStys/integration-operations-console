/*
 * File: PageCursorTest.java
 * Purpose: Independent legacy/filter-bound cursor and malformed-input rejection oracles by Jason Stys.
 * Symbols: exact timestamp/UUID and Base64 fixture assertions; docs/code-index.md has declaration locations.
 */
package dev.jasonstys.operations.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.jasonstys.operations.domain.JobStatus;

/** Pagination scope is a validated query contract, not a signature or access-control assertion. */
class PageCursorTest {
    @Test
    void legacyAndVersionTwoPreserveExactBoundariesAndScope() {
        Instant at = Instant.parse("2026-11-09T00:00:00.123456Z");
        UUID id = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        String legacy = Base64.getUrlEncoder().withoutPadding().encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
        assertThat(PageCursor.decode(legacy)).isEqualTo(new PageCursor(at, id));
        PageCursor scoped = new PageCursor(at, id, account, JobStatus.RETRY_WAIT);
        assertThat(PageCursor.decode(scoped.encode())).isEqualTo(scoped);
        scoped.requireScope(account, JobStatus.RETRY_WAIT);
        assertThatThrownBy(() -> scoped.requireScope(null, JobStatus.RETRY_WAIT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scoped.requireScope(account, JobStatus.QUEUED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageCursor.decode(legacy).requireScope(account, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedOversizedOrUnsupportedCursorCannotBecomeAQuery() {
        for (String value : new String[] {"", "=", "x".repeat(257), "eA", encode("3|a|b|c|d"),
                encode("2|bad-time|bad-id||"), encode("2|2026-11-09T00:00:00Z|" + UUID.randomUUID() + "||INVALID")}) {
            assertThatThrownBy(() -> PageCursor.decode(value)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> PageCursor.decode(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
