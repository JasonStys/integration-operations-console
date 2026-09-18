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

The UI holds the recent ten jobs and selected audit list, not the entire database. Attempts, page
size, request limit, input lengths, and token capacity are bounded to prevent accidental unbounded
work. At much larger scale, status counts would move to a summary table or metrics store rather
than scan the jobs table.
