-- File: V2__filtered_job_indexes.sql
-- Purpose: Support account/state review with descending keyset order; no application data is rewritten.
CREATE INDEX jobs_account_updated_idx ON sync_jobs (account_id, updated_at DESC, id DESC);
CREATE INDEX jobs_status_updated_idx ON sync_jobs (status, updated_at DESC, id DESC);
CREATE INDEX jobs_account_status_updated_idx ON sync_jobs (account_id, status, updated_at DESC, id DESC);
CREATE INDEX audit_job_id_idx ON audit_events (job_id, id DESC);
