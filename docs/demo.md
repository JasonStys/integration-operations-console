# Demonstration walkthrough

1. Start the stack and open <http://localhost:8081>.
2. Link an Atlas or Beacon fictional account. Submit it again to observe account deduplication.
3. Submit a healthy job and press **Run next eligible job** until it succeeds across its pages.
4. Select **Audit** to inspect submission, scheduler, page, and success evidence.
5. Submit a permanent-failure job, run it, observe `DEAD LETTERED`, then replay it.
6. Submit a transient-failure job. Its audit record shows the deterministic next attempt time.
7. Submit a queued job and cancel it; terminal work rejects invalid transitions.
8. Refresh the page to confirm PostgreSQL-backed state survives the browser session.

The same happy path is automated by `scripts/demo.sh`. All names, provider records, failures, and
roles are synthetic.
