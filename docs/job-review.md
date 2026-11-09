# Job search and captured review

Use the console to investigate fictional integration work without a real provider account. The
recent activity feed offers quick actions; **Search all integration jobs** reaches older jobs too.

1. Link an Atlas Ads or Beacon CRM account. Set its provider reference explicitly. Repeating a
   connector/reference pair returns the original account; use a new reference for another account.
2. Submit a normal or failure-scenario sync. Keep the request key to retry that exact submission;
   **New request key** starts a separate sync. A changed account/scenario with the same key is an
   error, preventing silent reuse of another request.
3. Run the next eligible page, or a named job from recent activity. Normal jobs stream several
   bounded provider pages. Rate-limited/transient work waits until its stored availability time;
   permanent failures dead-letter. Cancellation and explicit replay retain their audit evidence.
4. Apply account and state filters. Previous/next navigation stays in that query. An empty list
   means no matches, not that the provider is healthy. Updating jobs can move them between pages.
5. Select **Review** or **Audit**. The review captures current status, accepted-record count,
   provider cursor, availability delay and a chronological event window together. The clock and
   delay describe capture time, not the time of an older event or a continuously updating timer.
6. Browse **Older audit events**, return to **Newest**, or refresh. Download saves exactly the
   displayed JSON snapshot without querying again. Newer responses and active mutations invalidate
   old review context, so a stale response cannot silently replace the selected job's evidence.

A read-only viewer can call GET routes and inspect evidence. Operator changes require the separate
demo role header; the browser illustrates the operator workflow. This is deliberately not a secure
multi-user deployment. Do not expose it with real credentials or customer information.

Troubleshooting: refresh after concurrent work; retry a failed review; correct malformed cursors or
clear filters; use a fresh request key only for a new request. A zero availability delay means the
scheduler may consider the job, not that a provider will accept it. Inspect permanent failure
details before replay. No report itself runs a job, changes its state or discards earlier events.
