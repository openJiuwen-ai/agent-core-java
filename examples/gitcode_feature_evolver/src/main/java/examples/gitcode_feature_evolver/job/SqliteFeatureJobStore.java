/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import examples.gitcode_feature_evolver.FeatureWorkflowMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of the independent feature workflow store.
 *
 * @since 0.1.12
 */
public final class SqliteFeatureJobStore implements FeatureJobStore {
    private static final int SCHEMA_VERSION = 4;
    private final String jdbcUrl;
    private final String repositoryScope;

    /**
     * Open or create the feature workflow database.
     *
     * @param databaseFile database path below an external data directory
     */
    public SqliteFeatureJobStore(Path databaseFile) {
        this(databaseFile, "");
    }

    /**
     * Open or create a store whose scheduler queries are limited to one repository.
     *
     * @param databaseFile database path below an external data directory
     * @param repositoryScope canonical owner/name, or empty for an administrative store
     */
    public SqliteFeatureJobStore(Path databaseFile, String repositoryScope) {
        Path path = Objects.requireNonNull(databaseFile, "databaseFile must not be null")
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            Class.forName("org.sqlite.JDBC");
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("Unable to prepare feature database", ex);
        }
        this.jdbcUrl = "jdbc:sqlite:" + path;
        this.repositoryScope = repositoryScope == null ? "" : repositoryScope.strip();
        initialize();
    }

    @Override
    public AdmissionResult admit(FeatureJobRequest request) {
        FeatureJobRequest required = Objects.requireNonNull(request, "request must not be null");
        requireWithinScope(required.repository());
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            AdmissionResult result = admitTransaction(connection, required);
            connection.commit();
            return result;
        } catch (SQLException ex) {
            throw failure("Unable to admit feature Issue", ex);
        }
    }

    private AdmissionResult admitTransaction(Connection connection, FeatureJobRequest request)
            throws SQLException {
        if (!insertDelivery(connection, request.delivery(), request.settings().observedAt())) {
            return existingAdmission(connection, request, AdmissionResult.Status.DELIVERY_ALREADY_SEEN);
        }
        String jobId = UUID.randomUUID().toString();
        if (!insertAdmission(connection, request, jobId)) {
            markDelivery(connection, request.delivery().id(), false, "issue already admitted");
            return existingAdmission(connection, request, AdmissionResult.Status.ISSUE_ALREADY_ADMITTED);
        }
        insertJob(connection, request, jobId);
        markDelivery(connection, request.delivery().id(), true, "admitted");
        insertAudit(connection, jobId, "ADMISSION",
                "Issue admitted via " + request.delivery().type(), request.settings().observedAt());
        return new AdmissionResult(AdmissionResult.Status.CREATED,
                Optional.of(selectById(connection, jobId).orElseThrow()));
    }

    @Override
    public Optional<FeatureJob> findById(String jobId) {
        try (Connection connection = connection()) {
            return selectById(connection, requireText(jobId, "jobId"));
        } catch (SQLException ex) {
            throw failure("Unable to read feature job", ex);
        }
    }

    @Override
    public Optional<FeatureJob> findByIssue(String repository, long issueIid) {
        requirePositive(issueIid, "issueIid");
        String sql = "SELECT j.* FROM feature_jobs j JOIN feature_admissions a ON a.job_id=j.id "
                + "WHERE a.repository=? AND a.issue_iid=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(repository, "repository"));
            statement.setLong(2, issueIid);
            return one(statement);
        } catch (SQLException ex) {
            throw failure("Unable to read feature admission", ex);
        }
    }

    @Override
    public Optional<FeatureJob> findByPullRequest(String repository, long pullRequestNumber) {
        requirePositive(pullRequestNumber, "pullRequestNumber");
        String sql = "SELECT * FROM feature_jobs WHERE repository=? AND pr_number=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(repository, "repository"));
            statement.setLong(2, pullRequestNumber);
            return one(statement);
        } catch (SQLException ex) {
            throw failure("Unable to find feature pull request", ex);
        }
    }

    @Override
    public Optional<FeatureJob> findBySystemTestPullRequest(long pullRequestNumber) {
        requirePositive(pullRequestNumber, "pullRequestNumber");
        String scope = hasRepositoryScope() ? " AND repository=?" : "";
        String sql = "SELECT * FROM feature_jobs WHERE system_test_pr_number=?" + scope;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, pullRequestNumber);
            if (hasRepositoryScope()) {
                statement.setString(2, repositoryScope);
            }
            return one(statement);
        } catch (SQLException ex) {
            throw failure("Unable to find system-test pull request", ex);
        }
    }

    @Override
    public boolean acceptDelivery(FeatureJobRequest.Delivery delivery, Instant observedAt,
                                  String reason) {
        FeatureJobRequest.Delivery required = Objects.requireNonNull(delivery, "delivery must not be null");
        Instant instant = Objects.requireNonNull(observedAt, "observedAt must not be null");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            boolean accepted = insertDelivery(connection, required, instant);
            if (accepted) {
                markDelivery(connection, required.id(), true, safe(reason));
            }
            connection.commit();
            return accepted;
        } catch (SQLException ex) {
            throw failure("Unable to record feature delivery", ex);
        }
    }

    @Override
    public Optional<FeatureJob> leaseNext(String workerId, Instant now, Duration duration) {
        String owner = requireText(workerId, "workerId");
        Instant instant = Objects.requireNonNull(now, "now must not be null");
        Duration leaseDuration = requirePositive(duration, "duration");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            Optional<FeatureJob> leased = leaseTransaction(connection, owner, instant, leaseDuration);
            connection.commit();
            return leased;
        } catch (SQLException ex) {
            throw failure("Unable to lease feature job", ex);
        }
    }

    private Optional<FeatureJob> leaseTransaction(Connection connection, String owner,
                                                  Instant now, Duration duration) throws SQLException {
        Optional<FeatureJob> selected = selectLeaseCandidate(connection, now.toEpochMilli());
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        FeatureJob job = selected.orElseThrow();
        String sql = "UPDATE feature_jobs SET lease_owner=?,lease_until=?,version=version+1,updated_at=? "
                + "WHERE id=? AND version=? AND (lease_owner='' OR lease_until<?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner);
            statement.setLong(2, now.plus(duration).toEpochMilli());
            statement.setLong(3, now.toEpochMilli());
            statement.setString(4, job.identity().id());
            statement.setLong(5, job.record().version());
            statement.setLong(6, now.toEpochMilli());
            if (statement.executeUpdate() != 1) {
                return Optional.empty();
            }
        }
        insertAudit(connection, job.identity().id(), "WORKER",
                "Stage " + job.progress().stage().name() + " started", now);
        return selectById(connection, job.identity().id());
    }

    @Override
    public boolean heartbeat(String jobId, String workerId, Instant now, Duration duration) {
        String sql = "UPDATE feature_jobs SET lease_until=?,updated_at=? WHERE id=? AND lease_owner=?";
        Instant instant = Objects.requireNonNull(now, "now must not be null");
        Duration requiredDuration = requirePositive(duration, "duration");
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instant.plus(requiredDuration).toEpochMilli());
            statement.setLong(2, instant.toEpochMilli());
            statement.setString(3, requireText(jobId, "jobId"));
            statement.setString(4, requireText(workerId, "workerId"));
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw failure("Unable to refresh feature lease", ex);
        }
    }

    @Override
    public void recoverExpiredLeases(Instant now) {
        String scope = hasRepositoryScope() ? " AND repository=?" : "";
        String sql = "UPDATE feature_jobs SET lease_owner='',lease_until=0,version=version+1 "
                + "WHERE lease_owner<>'' AND lease_until<?" + scope;
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Objects.requireNonNull(now, "now must not be null").toEpochMilli());
            if (hasRepositoryScope()) {
                statement.setString(2, repositoryScope);
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("Unable to recover feature leases", ex);
        }
    }

    @Override
    public int releaseLeases(String workerId, Instant now) {
        String owner = requireText(workerId, "workerId");
        Instant instant = Objects.requireNonNull(now, "now must not be null");
        String scope = hasRepositoryScope() ? " AND repository=?" : "";
        String sql = "UPDATE feature_jobs SET lease_owner='',lease_until=0,version=version+1,"
                + "updated_at=? WHERE lease_owner=?" + scope;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instant.toEpochMilli());
            statement.setString(2, owner);
            if (hasRepositoryScope()) {
                statement.setString(3, repositoryScope);
            }
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("Unable to release feature leases", ex);
        }
    }

    @Override
    public FeatureJob transition(String jobId, long version, FeatureJobMutation mutation) {
        FeatureJobMutation required = Objects.requireNonNull(mutation, "mutation must not be null");
        String sql = "UPDATE feature_jobs SET state=?,resume_state=?,gate_round=?,task_attempt=?,"
                + "last_error=?,lease_owner='',lease_until=0,version=version+1,updated_at=? "
                + "WHERE id=? AND version=?";
        long now = System.currentTimeMillis();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            String requiredJobId = requireText(jobId, "jobId");
            FeatureJob before = selectById(connection, requiredJobId)
                    .orElseThrow(() -> new IllegalStateException("feature job no longer exists"));
            updateTransition(connection, sql, requiredJobId, version, required, now);
            if (resetsRecovery(before.progress().stage(), required.stage())) {
                resetRecovery(connection, requiredJobId);
            }
            insertAudit(connection, jobId, "TRANSITION", required.stage().name() + ": "
                    + safe(required.error()), Instant.ofEpochMilli(now));
            FeatureJob result = selectById(connection, jobId).orElseThrow();
            connection.commit();
            return result;
        } catch (SQLException ex) {
            throw failure("Unable to transition feature job", ex);
        }
    }

    @Override
    public FeatureJob recordPullRequest(String jobId, long version,
                                        FeatureJob.PullRequest pullRequest) {
        FeatureJob.PullRequest required = Objects.requireNonNull(
                pullRequest, "pullRequest must not be null");
        String sql = "UPDATE feature_jobs SET pr_number=?,pr_url=?,head_sha=?,draft=?,last_pr_check_at=?,"
                + "version=version+1,updated_at=? WHERE id=? AND version=?";
        long now = System.currentTimeMillis();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (required.number() == null) {
                    statement.setNull(1, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(1, required.number());
                }
                statement.setString(2, required.url());
                statement.setString(3, required.headSha());
                statement.setInt(4, required.draft() ? 1 : 0);
                statement.setLong(5, required.lastCheckedAt());
                statement.setLong(6, now);
                statement.setString(7, requireText(jobId, "jobId"));
                statement.setLong(8, version);
                requireOne(statement.executeUpdate(),
                        "feature pull-request binding changed concurrently");
            }
            insertAudit(connection, jobId, "PUBLISH", publicationDetail(required),
                    Instant.ofEpochMilli(now));
            FeatureJob result = selectById(connection, jobId).orElseThrow();
            connection.commit();
            return result;
        } catch (SQLException ex) {
            throw failure("Unable to bind feature pull request", ex);
        }
    }

    @Override
    public FeatureJob recordSystemTestPullRequest(String jobId, long version,
                                                  FeatureJob.PullRequest pullRequest) {
        FeatureJob.PullRequest required = Objects.requireNonNull(
                pullRequest, "pullRequest must not be null");
        String sql = "UPDATE feature_jobs SET system_test_pr_number=?,system_test_pr_url=?,"
                + "system_test_head_sha=?,system_test_draft=?,last_system_test_pr_check_at=?,"
                + "version=version+1,updated_at=? WHERE id=? AND version=?";
        long now = System.currentTimeMillis();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindPullRequest(statement, required);
                statement.setLong(6, now);
                statement.setString(7, requireText(jobId, "jobId"));
                statement.setLong(8, version);
                requireOne(statement.executeUpdate(),
                        "system-test pull-request binding changed concurrently");
            }
            insertAudit(connection, jobId, "SYSTEM_TEST_PUBLISH",
                    publicationDetail(required), Instant.ofEpochMilli(now));
            FeatureJob result = selectById(connection, jobId).orElseThrow();
            connection.commit();
            return result;
        } catch (SQLException ex) {
            throw failure("Unable to bind system-test pull request", ex);
        }
    }

    @Override
    public CommandResult applyCommand(FeatureCommand command) {
        FeatureCommand required = Objects.requireNonNull(command, "command must not be null");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            CommandResult result = commandTransaction(connection, required);
            connection.commit();
            return result;
        } catch (SQLException ex) {
            throw failure("Unable to apply feature command", ex);
        }
    }

    private CommandResult commandTransaction(Connection connection, FeatureCommand command)
            throws SQLException {
        if (!insertCommand(connection, command)) {
            return new CommandResult(CommandResult.Status.ALREADY_SEEN,
                    findIssue(connection, command.identity()), "comment already processed");
        }
        Optional<FeatureJob> found = findIssue(connection, command.identity());
        if (found.isEmpty()) {
            return new CommandResult(CommandResult.Status.JOB_NOT_FOUND, Optional.empty(), "feature job not found");
        }
        FeatureJob job = found.orElseThrow();
        if (command.action() == FeatureCommand.Action.STATUS) {
            insertAudit(connection, job.identity().id(), "COMMAND", command.actor() + " requested status",
                    command.observedAt());
            return new CommandResult(CommandResult.Status.STATUS_ONLY, Optional.of(job), "status requested");
        }
        Optional<FeatureJobMutation> mutation = commandMutation(job, command);
        if (mutation.isEmpty()) {
            return new CommandResult(CommandResult.Status.INVALID_FOR_STATE, Optional.of(job),
                    "command is invalid for stage " + job.progress().stage());
        }
        FeatureJob updated = transitionInTransaction(connection, job, mutation.orElseThrow(), command);
        return new CommandResult(CommandResult.Status.APPLIED, Optional.of(updated), "command applied");
    }

    @Override
    public Optional<FeatureScanCheckpoint> loadCheckpoint(String repository, String label) {
        String sql = "SELECT window_start,window_end,next_page FROM feature_scan_checkpoints "
                + "WHERE repository=? AND label=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(repository, "repository"));
            statement.setString(2, requireText(label, "label"));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                FeatureScanCheckpoint.Window window = new FeatureScanCheckpoint.Window(
                        Instant.ofEpochMilli(result.getLong("window_start")),
                        Instant.ofEpochMilli(result.getLong("window_end")));
                return Optional.of(new FeatureScanCheckpoint(repository, label, window,
                        result.getInt("next_page")));
            }
        } catch (SQLException ex) {
            throw failure("Unable to read feature scan checkpoint", ex);
        }
    }

    @Override
    public void saveCheckpoint(FeatureScanCheckpoint checkpoint) {
        FeatureScanCheckpoint required = Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        String sql = "INSERT INTO feature_scan_checkpoints(repository,label,window_start,"
                + "window_end,next_page,updated_at) "
                + "VALUES(?,?,?,?,?,?) ON CONFLICT(repository,label) DO UPDATE SET window_start=excluded.window_start,"
                + "window_end=excluded.window_end,next_page=excluded.next_page,updated_at=excluded.updated_at";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, required.repository());
            statement.setString(2, required.label());
            statement.setLong(3, required.window().start().toEpochMilli());
            statement.setLong(4, required.window().end().toEpochMilli());
            statement.setInt(5, required.nextPage());
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("Unable to save feature scan checkpoint", ex);
        }
    }

    @Override
    public void clearCheckpoint(String repository, String label) {
        String sql = "DELETE FROM feature_scan_checkpoints WHERE repository=? AND label=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(repository, "repository"));
            statement.setString(2, requireText(label, "label"));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("Unable to clear feature scan checkpoint", ex);
        }
    }

    @Override
    public List<FeatureJob> listPullRequestsForReconciliation(int limit) {
        requireLimit(limit);
        String scope = hasRepositoryScope() ? "AND repository=? " : "";
        String sql = "SELECT * FROM feature_jobs WHERE pr_number IS NOT NULL "
                + "AND state NOT IN ('MERGED','CLOSED','CANCELLED','BLOCKED_EXTERNAL',"
                + "'FAILED_AUTOMATION','FAILED_CONFIGURATION','FAILED_POLICY','FAILED_INTERNAL') "
                + "AND state NOT IN ('SYSTEM_TEST','REVIEW_SYSTEM_TEST','PUBLISH_SYSTEM_TEST',"
                + "'SYSTEM_TEST_READY_FOR_REVIEW') "
                + scope
                + "ORDER BY last_pr_check_at ASC,created_at ASC LIMIT ?";
        List<FeatureJob> jobs = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement);
            statement.setInt(parameter, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    jobs.add(job(result));
                }
            }
            return List.copyOf(jobs);
        } catch (SQLException ex) {
            throw failure("Unable to list feature pull requests", ex);
        }
    }

    @Override
    public List<FeatureJob> listSystemTestPullRequestsForReconciliation(int limit) {
        requireLimit(limit);
        String scope = hasRepositoryScope() ? "AND repository=? " : "";
        String sql = "SELECT * FROM feature_jobs WHERE system_test_pr_number IS NOT NULL "
                + "AND state NOT IN ('MERGED','CLOSED','CANCELLED','BLOCKED_EXTERNAL',"
                + "'FAILED_AUTOMATION','FAILED_CONFIGURATION','FAILED_POLICY','FAILED_INTERNAL') "
                + scope
                + "ORDER BY last_system_test_pr_check_at ASC,created_at ASC LIMIT ?";
        List<FeatureJob> jobs = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement);
            statement.setInt(parameter, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    jobs.add(job(result));
                }
            }
            return List.copyOf(jobs);
        } catch (SQLException ex) {
            throw failure("Unable to list system-test pull requests", ex);
        }
    }

    @Override
    public List<FeatureJob> listJobsForCommandPolling(int limit) {
        requireLimit(limit);
        String scope = hasRepositoryScope() ? "AND repository=? " : "";
        String sql = "SELECT * FROM feature_jobs WHERE state NOT IN "
                + "('MERGED','CLOSED','CANCELLED','BLOCKED_EXTERNAL','FAILED_AUTOMATION',"
                + "'FAILED_CONFIGURATION','FAILED_POLICY','FAILED_INTERNAL') " + scope
                + "ORDER BY created_at ASC LIMIT ?";
        List<FeatureJob> jobs = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement);
            statement.setInt(parameter, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    jobs.add(job(result));
                }
            }
            return List.copyOf(jobs);
        } catch (SQLException ex) {
            throw failure("Unable to list feature jobs for command polling", ex);
        }
    }

    @Override
    public List<FeatureJob> listRecentJobs(int limit) {
        requireLimit(limit);
        String where = hasRepositoryScope() ? " WHERE repository=?" : "";
        String sql = "SELECT * FROM feature_jobs" + where
                + " ORDER BY updated_at DESC,created_at DESC LIMIT ?";
        List<FeatureJob> jobs = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement);
            statement.setInt(parameter, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    jobs.add(job(result));
                }
            }
            return List.copyOf(jobs);
        } catch (SQLException ex) {
            throw failure("Unable to list recent feature jobs", ex);
        }
    }

    @Override
    public List<FeatureAuditEvent> listRecentAuditEvents(int limit) {
        requireLimit(limit);
        String where = hasRepositoryScope() ? " WHERE j.repository=?" : "";
        String sql = "SELECT a.id,a.job_id,a.event_type,a.detail,a.created_at "
                + "FROM feature_audit_events a JOIN feature_jobs j ON j.id=a.job_id"
                + where + " ORDER BY a.id DESC LIMIT ?";
        List<FeatureAuditEvent> events = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement);
            statement.setInt(parameter, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    events.add(new FeatureAuditEvent(result.getLong("id"),
                            result.getString("job_id"), result.getString("event_type"),
                            result.getString("detail"), result.getLong("created_at")));
                }
            }
            return List.copyOf(events);
        } catch (SQLException ex) {
            throw failure("Unable to list recent feature audit events", ex);
        }
    }

    @Override
    public FeatureJob recordFailure(String jobId, long version, FeatureFailure failure,
                                    FeatureFailureEvent.RepairAttempt attempt,
                                    long nextRetryAt) {
        String requiredJobId = requireText(jobId, "jobId");
        FeatureFailure requiredFailure = Objects.requireNonNull(failure,
                "failure must not be null");
        FeatureFailureEvent.RepairAttempt requiredAttempt = Objects.requireNonNull(
                attempt, "attempt must not be null");
        long now = System.currentTimeMillis();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            updateRecovery(connection, requiredJobId, version, requiredFailure,
                    requiredAttempt, nextRetryAt, now);
            insertFailure(connection, requiredJobId, requiredFailure, requiredAttempt, now);
            insertAudit(connection, requiredJobId, auditType(requiredAttempt.tier()),
                    requiredFailure.code() + ": " + requiredFailure.diagnostic().summary(),
                    Instant.ofEpochMilli(now));
            FeatureJob result = selectById(connection, requiredJobId).orElseThrow();
            connection.commit();
            return result;
        } catch (SQLException ex) {
            throw failure("Unable to record feature failure", ex);
        }
    }

    @Override
    public FeatureJob recordRecoveryProgress(String jobId, long version,
                                             FeatureFailureEvent.RepairAttempt attempt,
                                             String summary) {
        String requiredJobId = requireText(jobId, "jobId");
        FeatureFailureEvent.RepairAttempt requiredAttempt = Objects.requireNonNull(
                attempt, "attempt must not be null");
        long now = System.currentTimeMillis();
        String sql = "UPDATE feature_jobs SET " + recoveryCounter(requiredAttempt.tier())
                + "=?,next_retry_at=0,retry_stage=NULL,updated_at=? WHERE id=? AND version=?";
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, requiredAttempt.number());
                statement.setLong(2, now);
                statement.setString(3, requiredJobId);
                statement.setLong(4, version);
                requireOne(statement.executeUpdate(), "feature job changed concurrently");
            }
            insertAudit(connection, requiredJobId, auditType(requiredAttempt.tier()),
                    safe(summary), Instant.ofEpochMilli(now));
            FeatureJob result = selectById(connection, requiredJobId).orElseThrow();
            connection.commit();
            return result;
        } catch (SQLException ex) {
            throw failure("Unable to record feature recovery progress", ex);
        }
    }

    @Override
    public List<FeatureFailureEvent> listFailureEvents(String jobId, int limit) {
        requireLimit(limit);
        String sql = "SELECT * FROM feature_failure_events WHERE job_id=? ORDER BY id DESC LIMIT ?";
        List<FeatureFailureEvent> events = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(jobId, "jobId"));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    events.add(failureEvent(result));
                }
            }
            return List.copyOf(events);
        } catch (SQLException ex) {
            throw failure("Unable to list feature failures", ex);
        }
    }

    @Override
    public Optional<ApprovedGateReceipt> findGateReceipt(String jobId, FeatureStage stage,
                                                         String profile, String fingerprint) {
        String sql = "SELECT * FROM feature_gate_receipts WHERE job_id=? AND stage=? "
                + "AND profile=? AND input_fingerprint=?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(jobId, "jobId"));
            statement.setString(2, Objects.requireNonNull(stage, "stage must not be null").name());
            statement.setString(3, requireText(profile, "profile"));
            statement.setString(4, requireText(fingerprint, "fingerprint"));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(gateReceipt(result, true)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw failure("Unable to read approved Gate receipt", ex);
        }
    }

    @Override
    public ApprovedGateReceipt recordGateReceipt(ApprovedGateReceipt receipt) {
        ApprovedGateReceipt required = Objects.requireNonNull(receipt, "receipt must not be null");
        String sql = "INSERT OR IGNORE INTO feature_gate_receipts(job_id,stage,profile,"
                + "input_fingerprint,selector_summary,status,failure_code,failure_category,"
                + "failure_summary,failure_details,exit_code,output_tail,completed_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindGateReceipt(statement, required);
                statement.executeUpdate();
            }
            insertAudit(connection, required.jobId(), "GATE",
                    required.stage() + "/" + required.identity().profile() + " "
                            + required.result().status(),
                    Instant.ofEpochMilli(required.completedAt()));
            ApprovedGateReceipt stored = selectGateReceipt(connection, required).orElseThrow();
            connection.commit();
            return stored;
        } catch (SQLException ex) {
            throw failure("Unable to record approved Gate receipt", ex);
        }
    }

    @Override
    public void discardGateReceipt(ApprovedGateReceipt receipt) {
        ApprovedGateReceipt required = Objects.requireNonNull(
                receipt, "receipt must not be null");
        String sql = "DELETE FROM feature_gate_receipts WHERE job_id=? AND stage=? "
                + "AND profile=? AND input_fingerprint=?";
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            int deleted;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, required.jobId());
                statement.setString(2, required.stage().name());
                statement.setString(3, required.identity().profile());
                statement.setString(4, required.identity().fingerprint());
                deleted = statement.executeUpdate();
            }
            if (deleted > 0) {
                insertAudit(connection, required.jobId(), "GATE",
                        required.stage() + "/" + required.identity().profile()
                                + " discarded non-cacheable receipt",
                        Instant.ofEpochMilli(required.completedAt()));
            }
            connection.commit();
        } catch (SQLException ex) {
            throw failure("Unable to discard approved Gate receipt", ex);
        }
    }

    @Override
    public Optional<ApprovedGateReceipt> findLatestGateReceipt(String jobId) {
        String sql = "SELECT * FROM feature_gate_receipts WHERE job_id=? "
                + "ORDER BY completed_at DESC,id DESC LIMIT 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(jobId, "jobId"));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(gateReceipt(
                        result, result.getInt("cache_hits") > 0)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw failure("Unable to read latest approved Gate receipt", ex);
        }
    }

    @Override
    public void recordGateCacheHit(ApprovedGateReceipt receipt) {
        ApprovedGateReceipt required = Objects.requireNonNull(receipt, "receipt must not be null");
        String sql = "UPDATE feature_gate_receipts SET cache_hits=cache_hits+1 WHERE job_id=? "
                + "AND stage=? AND profile=? AND input_fingerprint=?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, required.jobId());
            statement.setString(2, required.stage().name());
            statement.setString(3, required.identity().profile());
            statement.setString(4, required.identity().fingerprint());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("Unable to record approved Gate cache hit", ex);
        }
    }

    private static void updateRecovery(Connection connection, String jobId, long version,
                                       FeatureFailure failure,
                                       FeatureFailureEvent.RepairAttempt attempt,
                                       long nextRetryAt, long now) throws SQLException {
        if ("FAILURE".equals(attempt.tier())) {
            updateFailureOnly(connection, jobId, version, failure, nextRetryAt, now);
            return;
        }
        String counter = recoveryCounter(attempt.tier());
        String sql = "UPDATE feature_jobs SET " + counter + "=?,next_retry_at=?,retry_stage=?,"
                + "failure_code=?,failure_category=?,last_error=?,updated_at=? "
                + "WHERE id=? AND version=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, attempt.number());
            statement.setLong(2, Math.max(0L, nextRetryAt));
            setOptionalStage(statement, 3,
                    nextRetryAt > 0L ? failure.originStage() : null);
            statement.setString(4, failure.code());
            statement.setString(5, failure.category().name());
            statement.setString(6, safe(failure.diagnostic().summary()));
            statement.setLong(7, now);
            statement.setString(8, jobId);
            statement.setLong(9, version);
            requireOne(statement.executeUpdate(), "feature job changed concurrently");
        }
    }

    private static void updateFailureOnly(Connection connection, String jobId, long version,
                                          FeatureFailure failure, long nextRetryAt, long now)
            throws SQLException {
        String sql = "UPDATE feature_jobs SET next_retry_at=?,retry_stage=NULL,failure_code=?,"
                + "failure_category=?,last_error=?,updated_at=? WHERE id=? AND version=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Math.max(0L, nextRetryAt));
            statement.setString(2, failure.code());
            statement.setString(3, failure.category().name());
            statement.setString(4, safe(failure.diagnostic().summary()));
            statement.setLong(5, now);
            statement.setString(6, jobId);
            statement.setLong(7, version);
            requireOne(statement.executeUpdate(), "feature job changed concurrently");
        }
    }

    private static String recoveryCounter(String tier) {
        return switch (tier) {
            case "PRIMARY" -> "primary_repair_round";
            case "DIAGNOSTIC" -> "diagnostic_repair_round";
            case "RETRY" -> "transient_retry_count";
            case "PREFETCH" -> "dependency_prefetch_round";
            default -> throw new IllegalArgumentException("unsupported recovery tier");
        };
    }

    private static String auditType(String tier) {
        return switch (tier) {
            case "PRIMARY", "DIAGNOSTIC" -> "REPAIR";
            case "RETRY" -> "RETRY";
            case "PREFETCH" -> "PREFETCH";
            case "FAILURE" -> "FAILURE";
            default -> "FAILURE";
        };
    }

    private static void insertFailure(Connection connection, String jobId,
                                      FeatureFailure failure,
                                      FeatureFailureEvent.RepairAttempt attempt,
                                      long now) throws SQLException {
        String sql = "INSERT INTO feature_failure_events(job_id,stage,repair_tier,attempt,code,"
                + "category,recovery_stage,summary,details,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setString(2, failure.originStage().name());
            statement.setString(3, attempt.tier());
            statement.setInt(4, attempt.number());
            statement.setString(5, failure.code());
            statement.setString(6, failure.category().name());
            setOptionalStage(statement, 7, failure.recoveryStage());
            statement.setString(8, safe(failure.diagnostic().summary()));
            statement.setString(9, safeDetails(failure.diagnostic().details()));
            statement.setLong(10, now);
            statement.executeUpdate();
        }
    }

    private static FeatureFailureEvent failureEvent(ResultSet result) throws SQLException {
        FeatureFailure failure = new FeatureFailure(result.getString("code"),
                FeatureFailureCategory.valueOf(result.getString("category")),
                FeatureStage.valueOf(result.getString("stage")),
                optionalStage(result.getString("recovery_stage")),
                new FeatureFailure.Diagnostic(result.getString("summary"),
                        result.getString("details")));
        return new FeatureFailureEvent(result.getLong("id"), result.getString("job_id"),
                new FeatureFailureEvent.RepairAttempt(result.getString("repair_tier"),
                        result.getInt("attempt")), failure, result.getLong("created_at"));
    }

    @Override
    public void markPullRequestChecked(String jobId, long checkedAt) {
        String sql = "UPDATE feature_jobs SET last_pr_check_at=?,updated_at=? WHERE id=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, checkedAt);
            statement.setLong(2, checkedAt);
            statement.setString(3, requireText(jobId, "jobId"));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("Unable to mark feature pull request checked", ex);
        }
    }

    @Override
    public void markSystemTestPullRequestChecked(String jobId, long checkedAt) {
        String sql = "UPDATE feature_jobs SET last_system_test_pr_check_at=?,updated_at=? WHERE id=?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, checkedAt);
            statement.setLong(2, checkedAt);
            statement.setString(3, requireText(jobId, "jobId"));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("Unable to mark system-test pull request checked", ex);
        }
    }

    @Override
    public void close() {
        // Connections are intentionally short lived so scheduler and worker threads do not share JDBC state.
    }

    private void initialize() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_schema_version (version INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO feature_schema_version(version) SELECT " + SCHEMA_VERSION
                    + " WHERE NOT EXISTS (SELECT 1 FROM feature_schema_version)");
            createAdmissions(statement);
            createDeliveries(statement);
            createJobs(statement);
            createCommands(statement);
            createCheckpoints(statement);
            createAudit(statement);
            createFailures(statement);
            createGateReceipts(statement);
            migrate(connection);
            createSystemTestIndexes(statement);
            validateSchemaVersion(connection);
        } catch (SQLException ex) {
            throw failure("Unable to initialize feature database", ex);
        }
    }

    private static void createAdmissions(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_admissions ("
                + "repository TEXT NOT NULL,issue_iid INTEGER NOT NULL,job_id TEXT NOT NULL UNIQUE,"
                + "admitted_at INTEGER NOT NULL,PRIMARY KEY(repository,issue_iid))");
    }

    private static void createDeliveries(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_deliveries ("
                + "delivery_id TEXT PRIMARY KEY,event_type TEXT NOT NULL,payload_hash TEXT NOT NULL,"
                + "received_at INTEGER NOT NULL,accepted INTEGER NOT NULL DEFAULT 0,reason TEXT NOT NULL DEFAULT '')");
    }

    private static void createJobs(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_jobs ("
                + "id TEXT PRIMARY KEY,repository TEXT NOT NULL,issue_iid INTEGER NOT NULL,issue_title TEXT NOT NULL,"
                + "issue_url TEXT NOT NULL,branch TEXT NOT NULL UNIQUE,artifact_root TEXT NOT NULL,mode TEXT NOT NULL,"
                + "state TEXT NOT NULL,resume_state TEXT,gate_round INTEGER NOT NULL DEFAULT 0,"
                + "task_attempt INTEGER NOT NULL DEFAULT 0,pr_number INTEGER,pr_url TEXT NOT NULL DEFAULT '',"
                + "head_sha TEXT NOT NULL DEFAULT '',draft INTEGER NOT NULL DEFAULT 1,"
                + "last_pr_check_at INTEGER NOT NULL DEFAULT 0,lease_owner TEXT NOT NULL DEFAULT '',"
                + "system_test_pr_number INTEGER,system_test_pr_url TEXT NOT NULL DEFAULT '',"
                + "system_test_head_sha TEXT NOT NULL DEFAULT '',system_test_draft INTEGER NOT NULL DEFAULT 1,"
                + "last_system_test_pr_check_at INTEGER NOT NULL DEFAULT 0,"
                + "lease_until INTEGER NOT NULL DEFAULT 0,version INTEGER NOT NULL DEFAULT 0,"
                + "primary_repair_round INTEGER NOT NULL DEFAULT 0,"
                + "diagnostic_repair_round INTEGER NOT NULL DEFAULT 0,"
                + "transient_retry_count INTEGER NOT NULL DEFAULT 0,"
                + "dependency_prefetch_round INTEGER NOT NULL DEFAULT 0,"
                + "next_retry_at INTEGER NOT NULL DEFAULT 0,"
                + "retry_stage TEXT,"
                + "failure_code TEXT NOT NULL DEFAULT '',failure_category TEXT,"
                + "last_error TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_feature_jobs_runnable "
                + "ON feature_jobs(state,lease_until,created_at)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_feature_jobs_pr "
                + "ON feature_jobs(last_pr_check_at) WHERE pr_number IS NOT NULL");
    }

    private static void createSystemTestIndexes(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_feature_jobs_system_test_pr "
                + "ON feature_jobs(last_system_test_pr_check_at) "
                + "WHERE system_test_pr_number IS NOT NULL");
    }

    private static void migrate(Connection connection) throws SQLException {
        int version = schemaVersion(connection);
        if (version == SCHEMA_VERSION) {
            return;
        }
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new SQLException("Unsupported feature database schema version");
        }
        try (Statement statement = connection.createStatement()) {
            if (version == 1) {
                migrateVersionOne(statement);
                version = 2;
            }
            if (version == 2) {
                migrateVersionTwo(statement);
                version = 3;
            }
            if (version == 3) {
                migrateVersionThree(statement);
            }
        }
    }

    private static void migrateVersionOne(Statement statement) throws SQLException {
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN system_test_pr_number INTEGER");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN system_test_pr_url "
                + "TEXT NOT NULL DEFAULT ''");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN system_test_head_sha "
                + "TEXT NOT NULL DEFAULT ''");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN system_test_draft "
                + "INTEGER NOT NULL DEFAULT 1");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN last_system_test_pr_check_at "
                + "INTEGER NOT NULL DEFAULT 0");
        statement.executeUpdate("UPDATE feature_schema_version SET version=2 WHERE version=1");
    }

    private static void migrateVersionTwo(Statement statement) throws SQLException {
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN primary_repair_round "
                + "INTEGER NOT NULL DEFAULT 0");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN diagnostic_repair_round "
                + "INTEGER NOT NULL DEFAULT 0");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN transient_retry_count "
                + "INTEGER NOT NULL DEFAULT 0");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN dependency_prefetch_round "
                + "INTEGER NOT NULL DEFAULT 0");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN next_retry_at "
                + "INTEGER NOT NULL DEFAULT 0");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN failure_code "
                + "TEXT NOT NULL DEFAULT ''");
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN failure_category TEXT");
        migrateLegacyStates(statement);
        statement.executeUpdate("UPDATE feature_schema_version SET version=3 WHERE version=2");
    }

    private static void migrateVersionThree(Statement statement) throws SQLException {
        statement.executeUpdate("ALTER TABLE feature_jobs ADD COLUMN retry_stage TEXT");
        statement.executeUpdate("UPDATE feature_jobs SET retry_stage=resume_state "
                + "WHERE state='RETRY_SCHEDULED'");
        statement.executeUpdate("UPDATE feature_jobs SET retry_stage='DEPENDENCY_PREFETCH',"
                + "resume_state=COALESCE((SELECT recovery_stage FROM feature_failure_events "
                + "WHERE job_id=feature_jobs.id AND repair_tier='PREFETCH' "
                + "ORDER BY id DESC LIMIT 1),resume_state) "
                + "WHERE state='RETRY_SCHEDULED' "
                + "AND failure_code LIKE 'DEPENDENCY_PREFETCH_%'");
        statement.executeUpdate("UPDATE feature_schema_version SET version=4 WHERE version=3");
    }

    private static void migrateLegacyStates(Statement statement) throws SQLException {
        statement.executeUpdate("UPDATE feature_jobs SET state='DESIGN' WHERE state='WAIT_R1_APPROVAL'");
        statement.executeUpdate("UPDATE feature_jobs SET state='IMPLEMENT_RED' "
                + "WHERE state='WAIT_R2_APPROVAL'");
        statement.executeUpdate("UPDATE feature_jobs SET state='SHIP' WHERE state='WAIT_R3_APPROVAL'");
        statement.executeUpdate("UPDATE feature_jobs SET state='RETRY_SCHEDULED',next_retry_at=0 "
                + "WHERE state='FAILED_RETRYABLE'");
        statement.executeUpdate("UPDATE feature_jobs SET state='DEPENDENCY_PREFETCH' "
                + "WHERE state='WAITING_DEPENDENCY_PREFETCH'");
        statement.executeUpdate("UPDATE feature_jobs SET state='FAILED_AUTOMATION' "
                + "WHERE state IN ('WAITING_HUMAN','FAILED_FINAL')");
        statement.executeUpdate("UPDATE feature_jobs SET resume_state='DESIGN' "
                + "WHERE resume_state='WAIT_R1_APPROVAL'");
        statement.executeUpdate("UPDATE feature_jobs SET resume_state='IMPLEMENT_RED' "
                + "WHERE resume_state='WAIT_R2_APPROVAL'");
        statement.executeUpdate("UPDATE feature_jobs SET resume_state='SHIP' "
                + "WHERE resume_state='WAIT_R3_APPROVAL'");
        statement.executeUpdate("UPDATE feature_jobs SET mode='UNATTENDED' WHERE mode='ATTENDED'");
    }

    private static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COALESCE(MAX(version),0) FROM feature_schema_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static void createCommands(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_commands ("
                + "comment_id TEXT PRIMARY KEY,repository TEXT NOT NULL,issue_iid INTEGER NOT NULL,"
                + "actor TEXT NOT NULL,action TEXT NOT NULL,reason TEXT NOT NULL,observed_at INTEGER NOT NULL)");
    }

    private static void createCheckpoints(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_scan_checkpoints ("
                + "repository TEXT NOT NULL,label TEXT NOT NULL,window_start INTEGER NOT NULL,"
                + "window_end INTEGER NOT NULL,next_page INTEGER NOT NULL,updated_at INTEGER NOT NULL,"
                + "PRIMARY KEY(repository,label))");
    }

    private static void createAudit(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_audit_events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,job_id TEXT NOT NULL,event_type TEXT NOT NULL,"
                + "detail TEXT NOT NULL,created_at INTEGER NOT NULL)");
    }

    private static void createFailures(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_failure_events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,job_id TEXT NOT NULL,stage TEXT NOT NULL,"
                + "repair_tier TEXT NOT NULL,attempt INTEGER NOT NULL,code TEXT NOT NULL,"
                + "category TEXT NOT NULL,recovery_stage TEXT,summary TEXT NOT NULL,"
                + "details TEXT NOT NULL,created_at INTEGER NOT NULL)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_feature_failures_job "
                + "ON feature_failure_events(job_id,id DESC)");
    }

    private static void createGateReceipts(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS feature_gate_receipts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,job_id TEXT NOT NULL,stage TEXT NOT NULL,"
                + "profile TEXT NOT NULL,input_fingerprint TEXT NOT NULL,"
                + "selector_summary TEXT NOT NULL,status TEXT NOT NULL,failure_code TEXT,"
                + "failure_category TEXT,failure_summary TEXT NOT NULL DEFAULT '',"
                + "failure_details TEXT NOT NULL DEFAULT '',exit_code INTEGER NOT NULL,"
                + "output_tail TEXT NOT NULL,completed_at INTEGER NOT NULL,"
                + "cache_hits INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(job_id,stage,profile,input_fingerprint))");
    }

    private static void validateSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(version) FROM feature_schema_version")) {
            if (!result.next() || result.getInt(1) != SCHEMA_VERSION) {
                throw new SQLException("Unsupported feature database schema version");
            }
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private static boolean insertDelivery(Connection connection, FeatureJobRequest.Delivery delivery,
                                          Instant observedAt) throws SQLException {
        String sql = "INSERT OR IGNORE INTO feature_deliveries(delivery_id,event_type,payload_hash,received_at) "
                + "VALUES(?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, delivery.id());
            statement.setString(2, delivery.type());
            statement.setString(3, delivery.payloadHash());
            statement.setLong(4, observedAt.toEpochMilli());
            return statement.executeUpdate() == 1;
        }
    }

    private static boolean insertAdmission(Connection connection, FeatureJobRequest request,
                                           String jobId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO feature_admissions(repository,issue_iid,job_id,admitted_at) "
                + "VALUES(?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.repository());
            statement.setLong(2, request.issue().iid());
            statement.setString(3, jobId);
            statement.setLong(4, request.settings().observedAt().toEpochMilli());
            return statement.executeUpdate() == 1;
        }
    }

    private static void insertJob(Connection connection, FeatureJobRequest request, String jobId)
            throws SQLException {
        String sql = "INSERT INTO feature_jobs(id,repository,issue_iid,issue_title,issue_url,branch,"
                + "artifact_root,mode,state,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        long observedAt = request.settings().observedAt().toEpochMilli();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setString(2, request.repository());
            statement.setLong(3, request.issue().iid());
            statement.setString(4, request.issue().title());
            statement.setString(5, request.issue().url());
            statement.setString(6, request.branch());
            statement.setString(7, request.settings().artifactRoot());
            statement.setString(8, FeatureWorkflowMode.UNATTENDED.name());
            statement.setString(9, FeatureStage.ADMITTED.name());
            statement.setLong(10, observedAt);
            statement.setLong(11, observedAt);
            statement.executeUpdate();
        }
    }

    private static void markDelivery(Connection connection, String deliveryId,
                                     boolean accepted, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE feature_deliveries SET accepted=?,reason=? WHERE delivery_id=?")) {
            statement.setInt(1, accepted ? 1 : 0);
            statement.setString(2, reason);
            statement.setString(3, deliveryId);
            statement.executeUpdate();
        }
    }

    private AdmissionResult existingAdmission(Connection connection, FeatureJobRequest request,
                                               AdmissionResult.Status status) throws SQLException {
        Optional<FeatureJob> job = findIssue(connection, new FeatureCommand.Identity(
                request.delivery().id(), request.repository(), request.issue().iid()));
        return new AdmissionResult(status, job);
    }

    private Optional<FeatureJob> selectLeaseCandidate(Connection connection, long now)
            throws SQLException {
        String states = runnableStates();
        String scope = hasRepositoryScope() ? "AND repository=? " : "";
        String sql = "SELECT * FROM feature_jobs WHERE state IN (" + states + ") "
                + "AND (state<>'RETRY_SCHEDULED' OR next_retry_at<=?) "
                + "AND (lease_owner='' OR lease_until<?) " + scope
                + "ORDER BY created_at ASC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            if (hasRepositoryScope()) {
                statement.setString(3, repositoryScope);
            }
            return one(statement);
        }
    }

    private static String runnableStates() {
        return java.util.Arrays.stream(FeatureStage.values())
                .filter(FeatureStage::isRunnable)
                .map(stage -> "'" + stage.name() + "'")
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static void updateTransition(Connection connection, String sql, String jobId,
                                         long version, FeatureJobMutation mutation, long now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mutation.stage().name());
            if (mutation.resumeStage() == null) {
                statement.setNull(2, java.sql.Types.VARCHAR);
            } else {
                statement.setString(2, mutation.resumeStage().name());
            }
            statement.setInt(3, mutation.gateRound());
            statement.setInt(4, mutation.taskAttempt());
            statement.setString(5, safe(mutation.error()));
            statement.setLong(6, now);
            statement.setString(7, jobId);
            statement.setLong(8, version);
            requireOne(statement.executeUpdate(), "feature job changed concurrently");
        }
    }

    private static boolean resetsRecovery(FeatureStage before, FeatureStage after) {
        if (before == after || after.isTerminal()) {
            return false;
        }
        return before != FeatureStage.RETRY_SCHEDULED
                && before != FeatureStage.DEPENDENCY_PREFETCH
                && after != FeatureStage.RETRY_SCHEDULED
                && after != FeatureStage.DEPENDENCY_PREFETCH;
    }

    private static void resetRecovery(Connection connection, String jobId) throws SQLException {
        String sql = "UPDATE feature_jobs SET primary_repair_round=0,"
                + "diagnostic_repair_round=0,transient_retry_count=0,"
                + "dependency_prefetch_round=0,next_retry_at=0,retry_stage=NULL,failure_code='',"
                + "failure_category=NULL WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            requireOne(statement.executeUpdate(), "feature recovery state changed concurrently");
        }
    }

    private static boolean insertCommand(Connection connection, FeatureCommand command)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO feature_commands(comment_id,repository,issue_iid,actor,action,"
                + "reason,observed_at) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, command.identity().commentId());
            statement.setString(2, command.identity().repository());
            statement.setLong(3, command.identity().issueIid());
            statement.setString(4, command.actor());
            statement.setString(5, command.action().name());
            statement.setString(6, safe(command.reason()));
            statement.setLong(7, command.observedAt().toEpochMilli());
            return statement.executeUpdate() == 1;
        }
    }

    private static Optional<FeatureJobMutation> commandMutation(FeatureJob job, FeatureCommand command) {
        return switch (command.action()) {
            case PAUSE -> pause(job, command.reason());
            case RESUME -> resume(job, command.reason());
            case CANCEL -> cancel(job, command.reason());
            case STATUS -> Optional.empty();
        };
    }

    private static Optional<FeatureJobMutation> pause(FeatureJob job, String reason) {
        FeatureStage current = job.progress().stage();
        if (current.isTerminal() || current == FeatureStage.PAUSED || current == FeatureStage.CANCEL_REQUESTED) {
            return Optional.empty();
        }
        return Optional.of(new FeatureJobMutation(FeatureStage.PAUSED, current,
                job.progress().gateRound(), job.progress().taskAttempt(), "paused: " + reason));
    }

    private static Optional<FeatureJobMutation> resume(FeatureJob job, String reason) {
        FeatureStage resume = job.progress().resumeStage();
        FeatureStage current = job.progress().stage();
        boolean resumable = current == FeatureStage.PAUSED;
        if (!resumable || resume == null) {
            return Optional.empty();
        }
        return Optional.of(new FeatureJobMutation(resume, null, job.progress().gateRound(),
                job.progress().taskAttempt(), "resumed: " + reason));
    }

    private static Optional<FeatureJobMutation> cancel(FeatureJob job, String reason) {
        if (job.progress().stage().isTerminal()) {
            return Optional.empty();
        }
        return Optional.of(mutation(job, FeatureStage.CANCEL_REQUESTED,
                job.progress().gateRound(), "cancel requested: " + reason));
    }

    private static FeatureJobMutation mutation(FeatureJob job, FeatureStage stage,
                                               int round, String reason) {
        return new FeatureJobMutation(stage, null, round, job.progress().taskAttempt(), reason);
    }

    private static FeatureJob transitionInTransaction(Connection connection, FeatureJob job,
                                                       FeatureJobMutation mutation,
                                                       FeatureCommand command) throws SQLException {
        String sql = "UPDATE feature_jobs SET state=?,resume_state=?,gate_round=?,task_attempt=?,"
                + "last_error=?,lease_owner='',lease_until=0,version=version+1,updated_at=? "
                + "WHERE id=? AND version=?";
        long now = command.observedAt().toEpochMilli();
        updateTransition(connection, sql, job.identity().id(), job.record().version(), mutation, now);
        insertAudit(connection, job.identity().id(), "COMMAND", command.actor() + " "
                + command.action().name(), command.observedAt());
        return selectById(connection, job.identity().id()).orElseThrow();
    }

    private static Optional<FeatureJob> findIssue(Connection connection, FeatureCommand.Identity identity)
            throws SQLException {
        String sql = "SELECT j.* FROM feature_jobs j JOIN feature_admissions a ON a.job_id=j.id "
                + "WHERE a.repository=? AND a.issue_iid=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identity.repository());
            statement.setLong(2, identity.issueIid());
            return one(statement);
        }
    }

    private static Optional<FeatureJob> selectById(Connection connection, String jobId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM feature_jobs WHERE id=?")) {
            statement.setString(1, jobId);
            return one(statement);
        }
    }

    private static Optional<FeatureJob> one(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            return result.next() ? Optional.of(job(result)) : Optional.empty();
        }
    }

    private static FeatureJob job(ResultSet result) throws SQLException {
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                result.getLong("issue_iid"), result.getString("issue_title"), result.getString("issue_url"));
        FeatureJob.Identity identity = new FeatureJob.Identity(result.getString("id"),
                result.getString("repository"), issue, result.getString("branch"),
                result.getString("artifact_root"));
        FeatureJob.Progress progress = new FeatureJob.Progress(
                FeatureStage.valueOf(result.getString("state")), optionalStage(result.getString("resume_state")),
                FeatureWorkflowMode.valueOf(result.getString("mode")), result.getInt("gate_round"),
                result.getInt("task_attempt"));
        FeatureJob.PullRequest pullRequest = new FeatureJob.PullRequest(nullableLong(result, "pr_number"),
                result.getString("pr_url"), result.getString("head_sha"), result.getInt("draft") != 0,
                result.getLong("last_pr_check_at"));
        FeatureJob.PullRequest systemTestPullRequest = new FeatureJob.PullRequest(
                nullableLong(result, "system_test_pr_number"), result.getString("system_test_pr_url"),
                result.getString("system_test_head_sha"), result.getInt("system_test_draft") != 0,
                result.getLong("last_system_test_pr_check_at"));
        FeatureJob.Lease lease = new FeatureJob.Lease(
                result.getString("lease_owner"), result.getLong("lease_until"));
        FeatureJob.Recovery recovery = new FeatureJob.Recovery(
                new FeatureJob.RepairCounters(result.getInt("primary_repair_round"),
                        result.getInt("diagnostic_repair_round")),
                new FeatureJob.RetryCounters(result.getInt("transient_retry_count"),
                        result.getInt("dependency_prefetch_round")),
                result.getLong("next_retry_at"), optionalStage(result.getString("retry_stage")),
                result.getString("failure_code"),
                optionalFailureCategory(result.getString("failure_category")));
        FeatureJob.RecordMetadata metadata = new FeatureJob.RecordMetadata(
                result.getLong("version"), result.getString("last_error"), result.getLong("created_at"),
                result.getLong("updated_at"));
        return new FeatureJob(identity, progress,
                new FeatureJob.PullRequests(pullRequest, systemTestPullRequest), recovery,
                lease, metadata);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static FeatureStage optionalStage(String value) {
        return value == null || value.isBlank() ? null : FeatureStage.valueOf(value);
    }

    private static FeatureFailureCategory optionalFailureCategory(String value) {
        return value == null || value.isBlank() ? null : FeatureFailureCategory.valueOf(value);
    }

    private static void insertAudit(Connection connection, String jobId, String type,
                                    String detail, Instant at) throws SQLException {
        String sql = "INSERT INTO feature_audit_events(job_id,event_type,detail,created_at) VALUES(?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setString(2, type);
            statement.setString(3, safe(detail));
            statement.setLong(4, at.toEpochMilli());
            statement.executeUpdate();
        }
    }

    private static void bindGateReceipt(PreparedStatement statement,
                                        ApprovedGateReceipt receipt) throws SQLException {
        statement.setString(1, receipt.jobId());
        statement.setString(2, receipt.stage().name());
        statement.setString(3, receipt.identity().profile());
        statement.setString(4, receipt.identity().fingerprint());
        statement.setString(5, safe(receipt.identity().selectorSummary()));
        statement.setString(6, receipt.result().status().name());
        Optional<FeatureFailure> failure = receipt.result().failure();
        statement.setString(7, failure.map(FeatureFailure::code).orElse(null));
        statement.setString(8, failure.map(value -> value.category().name()).orElse(null));
        statement.setString(9, failure.map(value -> value.diagnostic().summary()).orElse(""));
        statement.setString(10, failure.map(value -> value.diagnostic().details()).orElse(""));
        statement.setInt(11, receipt.result().evidence().exitCode());
        statement.setString(12, receipt.result().evidence().outputTail());
        statement.setLong(13, receipt.completedAt());
    }

    private static Optional<ApprovedGateReceipt> selectGateReceipt(
            Connection connection, ApprovedGateReceipt receipt) throws SQLException {
        String sql = "SELECT * FROM feature_gate_receipts WHERE job_id=? AND stage=? "
                + "AND profile=? AND input_fingerprint=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, receipt.jobId());
            statement.setString(2, receipt.stage().name());
            statement.setString(3, receipt.identity().profile());
            statement.setString(4, receipt.identity().fingerprint());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(gateReceipt(result, false)) : Optional.empty();
            }
        }
    }

    private static ApprovedGateReceipt gateReceipt(ResultSet result, boolean cached)
            throws SQLException {
        FeatureStage stage = FeatureStage.valueOf(result.getString("stage"));
        String code = result.getString("failure_code");
        Optional<FeatureFailure> failure = code == null || code.isBlank()
                ? Optional.empty() : Optional.of(new FeatureFailure(code,
                FeatureFailureCategory.valueOf(result.getString("failure_category")),
                stage, stage, new FeatureFailure.Diagnostic(
                result.getString("failure_summary"), result.getString("failure_details"))));
        ApprovedGateReceipt.Identity identity = new ApprovedGateReceipt.Identity(
                result.getString("profile"), result.getString("input_fingerprint"),
                result.getString("selector_summary"));
        ApprovedGateReceipt.Result gateResult = new ApprovedGateReceipt.Result(
                ApprovedGateReceipt.Status.valueOf(result.getString("status")), failure,
                new ApprovedGateReceipt.Evidence(result.getInt("exit_code"),
                        result.getString("output_tail")), cached);
        return new ApprovedGateReceipt(result.getString("job_id"), stage, identity,
                gateResult, result.getLong("completed_at"));
    }

    private static void setOptionalStage(PreparedStatement statement, int index,
                                         FeatureStage stage) throws SQLException {
        if (stage == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, stage.name());
        }
    }

    private static String safeDetails(String detail) {
        String value = detail == null ? "" : detail.replace('\r', ' ').strip();
        return value.substring(0, Math.min(value.length(), 12_000));
    }

    private static void requireOne(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
    }

    private int bindScope(PreparedStatement statement) throws SQLException {
        if (!hasRepositoryScope()) {
            return 1;
        }
        statement.setString(1, repositoryScope);
        return 2;
    }

    private boolean hasRepositoryScope() {
        return !repositoryScope.isEmpty();
    }

    private void requireWithinScope(String repository) {
        if (hasRepositoryScope() && !repositoryScope.equals(repository)) {
            throw new IllegalArgumentException("repository is outside the configured store scope");
        }
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    private static String publicationDetail(FeatureJob.PullRequest pullRequest) {
        String head = pullRequest.headSha();
        String shortHead = head.substring(0, Math.min(head.length(), 12));
        String number = pullRequest.number() == null ? "pending" : "#" + pullRequest.number();
        String state = pullRequest.draft() ? "draft" : "ready";
        return "Commit " + shortHead + " published; PR " + number + " is " + state;
    }

    private static void bindPullRequest(PreparedStatement statement,
                                        FeatureJob.PullRequest pullRequest) throws SQLException {
        if (pullRequest.number() == null) {
            statement.setNull(1, java.sql.Types.BIGINT);
        } else {
            statement.setLong(1, pullRequest.number());
        }
        statement.setString(2, pullRequest.url());
        statement.setString(3, pullRequest.headSha());
        statement.setInt(4, pullRequest.draft() ? 1 : 0);
        statement.setLong(5, pullRequest.lastCheckedAt());
    }

    private static String safe(String value) {
        String normalized = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').strip();
        return normalized.substring(0, Math.min(normalized.length(), 1000));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static IllegalStateException failure(String message, SQLException cause) {
        return new IllegalStateException(message, cause);
    }
}
