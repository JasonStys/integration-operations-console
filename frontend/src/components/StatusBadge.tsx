/**
 * File: StatusBadge.tsx
 * Purpose: Consistent text-and-color job-state indicator.
 * Symbols: StatusBadge component and status input; exact lines are in docs/code-index.md.
 */
import type { JobStatus } from '../api/client';

interface StatusBadgeProps {
  status: JobStatus;
}

/** Displays state with text so meaning never relies on color alone. */
export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span className={`status status--${status.toLowerCase()}`}>{status.replace('_', ' ')}</span>
  );
}
