# Complexity and data-structure analysis

Let `n` be stored jobs, `k` the requested page size, `p` records in a provider page, and `a` the
configured maximum attempts (bounded at 10).

| Operation                | Time                        | Extra space      | Reason                                            |
| ------------------------ | --------------------------- | ---------------- | ------------------------------------------------- |
| Connector lookup         | O(1) average                | O(c) registry    | Immutable hash map keyed by connector ID          |
| Token-bucket decision    | O(1)                        | O(1)             | Constant arithmetic and state update              |
| Retry delay              | O(1)                        | O(1)             | Bounded exponent and identifier hash              |
| Account/job keyed lookup | O(log n)                    | O(1) client side | Database B-tree primary/unique index              |
| Idempotent insert        | O(log n)                    | O(1)             | Unique index check dominates                      |
| Ready-job selection      | O(log n + 1)                | O(1)             | Scheduler composite index and one-row limit       |
| Job page                 | O(log n + k)                | O(k)             | Seek to keyset then read the page; no offset scan |
| Status counts            | O(n)                        | O(s)             | Group scan; `s` is six bounded statuses           |
| Provider page normalize  | O(p)                        | O(p)             | Each emitted record is visited once               |
| Full logical sync        | O(total records + failures) | O(p) per run     | Cursor streams one bounded page at a time         |

Filtered listing has supporting `(account, updated, id)`, `(status, updated, id)` and
`(account, status, updated, id)` indexes. A suitable database plan can seek then read O(k) matching
rows; SQL optimizers and concurrent updates determine actual work. No cross-hardware speedup is
claimed. Bounded audit windows use `(job_id, id)` with an exclusive ID boundary and read at most
`k+1` rows (k<=100); reversal and JSON serialization require O(k) memory/time.

The UI holds the recent ten jobs, one 20-job page, one 25-event window and a navigation cursor
per visited page, rather than loading all jobs/events. Long navigation sessions grow the cursor
stack O(pages); changing filters resets it. Existing account lists and status-count scans remain
unbounded database operations, and the legacy audit API remains unbounded for compatibility.
Attempts, page
size, request limit, input lengths, and token capacity are bounded to prevent accidental unbounded
work. At much larger scale, status counts would move to a summary table or metrics store rather
than scan the jobs table.
