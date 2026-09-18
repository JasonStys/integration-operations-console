/*
 * File: JobStatus.java
 * Purpose: Defines every legal synchronization lifecycle state.
 * Symbols: JobStatus values; exact lines are indexed in docs/code-index.md.
 * State: enum constants only; no mutable variables.
 */
package dev.jasonstys.operations.domain;

/** Stable state names persisted in the database and returned by the API. */
public enum JobStatus {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    DEAD_LETTERED,
    CANCELLED;

    /**
     * Reports whether no more automatic work may occur.
     *
     * @return true for success, dead-letter, or cancellation
     */
    public boolean terminal() {
        return this == SUCCEEDED || this == DEAD_LETTERED || this == CANCELLED;
    }
}
