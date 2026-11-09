/*
 * File: PageCursor.java
 * Purpose: Opaque, URL-safe keyset-pagination cursor encoding.
 * Symbols: updatedAt/id components and encode()/decode(); exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.store;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import java.util.Objects;

import dev.jasonstys.operations.domain.JobStatus;

/**
 * Cursor for stable descending (updatedAt, id) database pagination.
 *
 * @param updatedAt timestamp of the last returned row
 * @param id tie-breaking UUID of the last returned row
 */
public record PageCursor(Instant updatedAt, UUID id, UUID accountId, JobStatus status) {
    /** Retain the original unfiltered constructor for existing service clients. */
    public PageCursor(Instant updatedAt, UUID id) {
        this(updatedAt, id, null, null);
    }

    /** Reject reuse with a different filter; this is query binding, not authentication. */
    public void requireScope(UUID account, JobStatus state) {
        if (!Objects.equals(accountId, account) || status != state) {
            throw new IllegalArgumentException("cursor belongs to different job filters");
        }
    }
    /** @return opaque Base64 URL cursor */
    public String encode() {
        String plain = "2|" + updatedAt + "|" + id + "|"
                + (accountId == null ? "" : accountId) + "|" + (status == null ? "" : status.name());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes and validates an untrusted client cursor in O(1) time.
     *
     * @param encoded URL-safe cursor
     * @return parsed cursor
     */
    public static PageCursor decode(String encoded) {
        try {
            if (encoded == null || !encoded.matches("[A-Za-z0-9_-]{1,256}")) {
                throw new IllegalArgumentException("cursor has invalid encoding or size");
            }
            String plain = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = plain.split("\\|", -1);
            if (parts.length == 2) {
                return new PageCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            }
            if (parts.length != 5 || !"2".equals(parts[0])) {
                throw new IllegalArgumentException("cursor has an invalid shape");
            }
            return new PageCursor(Instant.parse(parts[1]), UUID.fromString(parts[2]),
                    parts[3].isEmpty() ? null : UUID.fromString(parts[3]),
                    parts[4].isEmpty() ? null : JobStatus.valueOf(parts[4]));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }
}
