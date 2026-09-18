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

/**
 * Cursor for stable descending (updatedAt, id) database pagination.
 *
 * @param updatedAt timestamp of the last returned row
 * @param id tie-breaking UUID of the last returned row
 */
public record PageCursor(Instant updatedAt, UUID id) {
    /** @return opaque Base64 URL cursor */
    public String encode() {
        String plain = updatedAt + "|" + id;
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
            String plain = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = plain.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("cursor has an invalid shape");
            }
            return new PageCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }
}
