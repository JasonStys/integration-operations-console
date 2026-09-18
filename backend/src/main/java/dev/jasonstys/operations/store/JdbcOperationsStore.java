/*
 * File: JdbcOperationsStore.java
 * Purpose: Parameterized SQL implementation of the operations persistence port.
 * Symbols: ACCOUNT_ROW/JOB_ROW/AUDIT_ROW mappers and CRUD/query methods;
 * exact lines are generated in docs/code-index.md.
 * State: jdbc is immutable; all mutable data lives in database transactions.
 */
package dev.jasonstys.operations.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.stereotype.Repository;

import dev.jasonstys.operations.domain.AuditEvent;
import dev.jasonstys.operations.domain.ConnectorAccount;
import dev.jasonstys.operations.domain.FailurePlan;
import dev.jasonstys.operations.domain.JobStatus;
import dev.jasonstys.operations.domain.SyncJob;

/** JDBC store with bound parameters, keyset pagination, and explicit row mappings. */
@Repository
public class JdbcOperationsStore implements OperationsStore {
    private static final RowMapper<ConnectorAccount> ACCOUNT_ROW = (result, rowNumber) ->
            new ConnectorAccount(
                    result.getObject("id", UUID.class),
                    result.getString("connector_id"),
                    result.getString("display_name"),
                    result.getString("external_reference"),
                    instant(result, "created_at"));

    private static final RowMapper<SyncJob> JOB_ROW = (result, rowNumber) ->
            new SyncJob(
                    result.getObject("id", UUID.class),
                    result.getObject("account_id", UUID.class),
                    JobStatus.valueOf(result.getString("status")),
                    FailurePlan.valueOf(result.getString("failure_plan")),
                    result.getInt("attempt"),
                    result.getInt("max_attempts"),
                    result.getString("page_cursor"),
                    instant(result, "available_at"),
                    result.getString("correlation_id"),
                    result.getString("idempotency_key"),
                    result.getString("last_error"),
                    result.getInt("records_processed"),
                    instant(result, "created_at"),
                    instant(result, "updated_at"));

    private static final RowMapper<AuditEvent> AUDIT_ROW = (result, rowNumber) ->
            new AuditEvent(
                    result.getLong("id"),
                    result.getObject("job_id", UUID.class),
                    result.getString("event_type"),
                    result.getString("actor"),
                    result.getString("detail"),
                    instant(result, "occurred_at"));

    private final NamedParameterJdbcTemplate jdbc;
    private final boolean supportsOnConflict;

    /**
     * Detects the narrow dialect difference required for transaction-safe conflict handling.
     *
     * @param jdbc configured Spring named-parameter template
     */
    public JdbcOperationsStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        try {
            String productName = JdbcUtils.extractDatabaseMetaData(
                    Objects.requireNonNull(jdbc.getJdbcTemplate().getDataSource()),
                    metadata -> metadata.getDatabaseProductName());
            supportsOnConflict = "PostgreSQL".equals(productName);
        } catch (MetaDataAccessException exception) {
            throw new IllegalStateException("cannot identify database dialect", exception);
        }
    }

    @Override
    public AccountResult saveAccountIfAbsent(ConnectorAccount account) {
        String sql = """
                INSERT INTO connector_accounts
                  (id, connector_id, display_name, external_reference, created_at)
                VALUES
                  (:id, :connectorId, :displayName, :externalReference, :createdAt)
                """ + (supportsOnConflict
                    ? "ON CONFLICT (connector_id, external_reference) DO NOTHING" : "");
        int inserted;
        try {
            inserted = jdbc.update(sql, accountParameters(account));
        } catch (DuplicateKeyException exception) {
            if (supportsOnConflict) {
                throw exception;
            }
            inserted = 0;
        }
        if (inserted == 1) {
            return new AccountResult(account, true);
        }
        String existingSql = """
                SELECT * FROM connector_accounts
                WHERE connector_id = :connectorId AND external_reference = :externalReference
                """;
        List<ConnectorAccount> existing = jdbc.query(existingSql, accountParameters(account), ACCOUNT_ROW);
        if (existing.isEmpty()) {
            throw new IllegalStateException("account conflict did not resolve to an existing row");
        }
        return new AccountResult(existing.getFirst(), false);
    }

    @Override
    public Optional<ConnectorAccount> findAccount(UUID id) {
        List<ConnectorAccount> rows = jdbc.query(
                "SELECT * FROM connector_accounts WHERE id = :id",
                new MapSqlParameterSource("id", id), ACCOUNT_ROW);
        return rows.stream().findFirst();
    }

    @Override
    public List<ConnectorAccount> listAccounts() {
        return jdbc.query(
                "SELECT * FROM connector_accounts ORDER BY created_at, id",
                new MapSqlParameterSource(), ACCOUNT_ROW);
    }

    @Override
    public SubmissionResult saveJobIfAbsent(SyncJob job) {
        String sql = """
                INSERT INTO sync_jobs
                  (id, account_id, status, failure_plan, attempt, max_attempts, page_cursor,
                   available_at, correlation_id, idempotency_key, last_error,
                   records_processed, created_at, updated_at)
                VALUES
                  (:id, :accountId, :status, :failurePlan, :attempt, :maxAttempts, :pageCursor,
                   :availableAt, :correlationId, :idempotencyKey, :lastError,
                   :recordsProcessed, :createdAt, :updatedAt)
                """ + (supportsOnConflict
                    ? "ON CONFLICT (idempotency_key) DO NOTHING" : "");
        int inserted;
        try {
            inserted = jdbc.update(sql, jobParameters(job));
        } catch (DuplicateKeyException exception) {
            if (supportsOnConflict) {
                throw exception;
            }
            inserted = 0;
        }
        if (inserted == 1) {
            return new SubmissionResult(job, true);
        }
        List<SyncJob> existing = jdbc.query(
                "SELECT * FROM sync_jobs WHERE idempotency_key = :idempotencyKey",
                new MapSqlParameterSource("idempotencyKey", job.idempotencyKey()), JOB_ROW);
        if (existing.isEmpty()) {
            throw new IllegalStateException("job conflict did not resolve to an existing row");
        }
        return new SubmissionResult(existing.getFirst(), false);
    }

    @Override
    public Optional<SyncJob> findJob(UUID id) {
        List<SyncJob> rows = jdbc.query(
                "SELECT * FROM sync_jobs WHERE id = :id",
                new MapSqlParameterSource("id", id), JOB_ROW);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<SyncJob> findNextReady(Instant now) {
        String sql = """
                SELECT * FROM sync_jobs
                WHERE status IN ('QUEUED', 'RETRY_WAIT') AND available_at <= :now
                ORDER BY available_at, created_at, id
                LIMIT 1
                """;
        List<SyncJob> rows = jdbc.query(sql,
                new MapSqlParameterSource("now", databaseTime(now)), JOB_ROW);
        return rows.stream().findFirst();
    }

    @Override
    public void updateJob(SyncJob job) {
        String sql = """
                UPDATE sync_jobs SET
                  status = :status,
                  failure_plan = :failurePlan,
                  attempt = :attempt,
                  max_attempts = :maxAttempts,
                  page_cursor = :pageCursor,
                  available_at = :availableAt,
                  correlation_id = :correlationId,
                  last_error = :lastError,
                  records_processed = :recordsProcessed,
                  updated_at = :updatedAt
                WHERE id = :id
                """;
        if (jdbc.update(sql, jobParameters(job)) != 1) {
            throw new IllegalStateException("job disappeared during update: " + job.id());
        }
    }

    @Override
    public JobPage listJobs(int limit, PageCursor cursor) {
        int queryLimit = limit + 1;
        MapSqlParameterSource parameters = new MapSqlParameterSource("limit", queryLimit);
        String sql;
        if (cursor == null) {
            sql = "SELECT * FROM sync_jobs ORDER BY updated_at DESC, id DESC LIMIT :limit";
        } else {
            sql = """
                    SELECT * FROM sync_jobs
                    WHERE updated_at < :updatedAt
                       OR (updated_at = :updatedAt AND id < :id)
                    ORDER BY updated_at DESC, id DESC
                    LIMIT :limit
                    """;
            parameters.addValue("updatedAt", databaseTime(cursor.updatedAt()));
            parameters.addValue("id", cursor.id());
        }
        List<SyncJob> rows = new ArrayList<>(jdbc.query(sql, parameters, JOB_ROW));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows.removeLast();
        }
        String nextCursor = null;
        if (hasMore && !rows.isEmpty()) {
            SyncJob last = rows.getLast();
            nextCursor = new PageCursor(last.updatedAt(), last.id()).encode();
        }
        return new JobPage(rows, nextCursor);
    }

    @Override
    public void appendAudit(AuditEvent event) {
        String sql = """
                INSERT INTO audit_events (job_id, event_type, actor, detail, occurred_at)
                VALUES (:jobId, :eventType, :actor, :detail, :occurredAt)
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("jobId", event.jobId())
                .addValue("eventType", event.eventType())
                .addValue("actor", event.actor())
                .addValue("detail", event.detail())
                .addValue("occurredAt", databaseTime(event.occurredAt()));
        jdbc.update(sql, parameters);
    }

    @Override
    public List<AuditEvent> listAudit(UUID jobId) {
        return jdbc.query(
                "SELECT * FROM audit_events WHERE job_id = :jobId ORDER BY occurred_at, id",
                new MapSqlParameterSource("jobId", jobId), AUDIT_ROW);
    }

    @Override
    public List<StatusCount> countByStatus() {
        return jdbc.query(
                "SELECT status, COUNT(*) AS job_count FROM sync_jobs GROUP BY status ORDER BY status",
                new MapSqlParameterSource(),
                (result, rowNumber) -> new StatusCount(
                        result.getString("status"), result.getLong("job_count")));
    }

    private static MapSqlParameterSource accountParameters(ConnectorAccount account) {
        return new MapSqlParameterSource()
                .addValue("id", account.id())
                .addValue("connectorId", account.connectorId())
                .addValue("displayName", account.displayName())
                .addValue("externalReference", account.externalReference())
                .addValue("createdAt", databaseTime(account.createdAt()));
    }

    private static MapSqlParameterSource jobParameters(SyncJob job) {
        return new MapSqlParameterSource()
                .addValue("id", job.id())
                .addValue("accountId", job.accountId())
                .addValue("status", job.status().name())
                .addValue("failurePlan", job.failurePlan().name())
                .addValue("attempt", job.attempt())
                .addValue("maxAttempts", job.maxAttempts())
                .addValue("pageCursor", job.pageCursor(), Types.VARCHAR)
                .addValue("availableAt", databaseTime(job.availableAt()))
                .addValue("correlationId", job.correlationId())
                .addValue("idempotencyKey", job.idempotencyKey())
                .addValue("lastError", job.lastError(), Types.VARCHAR)
                .addValue("recordsProcessed", job.recordsProcessed())
                .addValue("createdAt", databaseTime(job.createdAt()))
                .addValue("updatedAt", databaseTime(job.updatedAt()));
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }
}
