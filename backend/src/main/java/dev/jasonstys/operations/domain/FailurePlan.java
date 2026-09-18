/*
 * File: FailurePlan.java
 * Purpose: Names deterministic provider failures available to the portfolio demo.
 * Symbols: FailurePlan values; exact lines are indexed in docs/code-index.md.
 * State: enum constants only; no credentials or external-provider state.
 */
package dev.jasonstys.operations.domain;

/** Explicit failure injection keeps retry demonstrations repeatable. */
public enum FailurePlan {
    NONE,
    RATE_LIMIT_ONCE,
    TRANSIENT_TWICE,
    PERMANENT
}
