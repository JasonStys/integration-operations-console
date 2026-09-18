/*
 * File: ForbiddenOperationException.java
 * Purpose: Marks a rejected demo-role mutation for centralized HTTP mapping.
 * Symbols: exception constructor; exact lines are indexed in docs/code-index.md.
 */
package dev.jasonstys.operations.web;

/** Raised when a viewer attempts an operator-only demonstration action. */
public final class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
