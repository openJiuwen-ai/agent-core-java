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
    private static final int SCHEMA_VERSION = 1;
    private final String jdbcUrl;

    /**
     * Open or create the feature workflow database.
     *
     * @param databaseFile database path below an external data directory
     */
    public SqliteFeatureJobStore(Path databaseFile) {
        Path path = Objects.requireNonNull(databaseFile, "databaseFile must not be null")
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            Class.forName("org.sqlite.JDBC");
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("Unable to prepare feature database", ex);
        }
        this.jdbcUrl = "jdbc:sqlite:" + path;
        initialize();
    }

    @Override
    public AdmissionResult admit(FeatureJobRequest request) {
        FeatureJobRequest required = Objects.requireNonNull(request, "request must not be null");
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
        insertAudit(connection, jobId, "ADMISSION", "Issue admitted", request.settings().observedAt());
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
        String sql = "UPDATE feature_jobs SET lease_owner='',lease_until=0,version=version+1 "
                + "WHERE lease_owner<>'' AND lease_until<?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Objects.requireNonNull(now, "now must not be null").toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("Unable to recover feature leases", ex);
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
            updateTransition(connection, sql, requireText(jobId, "jobId"), version, required, now);
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
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
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
            requireOne(statement.executeUpdate(), "feature pull-request binding changed concurrently");
            return selectById(connection, jobId).orElseThrow();
        } catch (SQLException ex) {
            throw failure("Unable to bind feature pull request", ex);
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
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        String sql = "SELECT * FROM feature_jobs WHERE pr_number IS NOT NULL "
                + "AND state NOT IN ('MERGED','CLOSED','CANCELLED','FAILED_FINAL') "
                + "ORDER BY last_pr_check_at ASC,created_at ASC LIMIT ?";
        List<FeatureJob> jobs = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
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
    public List<FeatureJob> listJobsForCommandPolling(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        String sql = "SELECT * FROM feature_jobs WHERE state NOT IN "
                + "('MERGED','CLOSED','CANCELLED','FAILED_FINAL') ORDER BY created_at ASC LIMIT ?";
        List<FeatureJob> jobs = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
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
                + "lease_until INTEGER NOT NULL DEFAULT 0,version INTEGER NOT NULL DEFAULT 0,"
                + "last_error TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_feature_jobs_runnable "
                + "ON feature_jobs(state,lease_until,created_at)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_feature_jobs_pr "
                + "ON feature_jobs(last_pr_check_at) WHERE pr_number IS NOT NULL");
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
            statement.setString(8, request.settings().mode().name());
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

    private static Optional<FeatureJob> selectLeaseCandidate(Connection connection, long now)
            throws SQLException {
        String states = runnableStates();
        String sql = "SELECT * FROM feature_jobs WHERE state IN (" + states + ") "
                + "AND (lease_owner='' OR lease_until<?) ORDER BY created_at ASC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
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
        FeatureStage current = job.progress().stage();
        int round = job.progress().gateRound();
        return switch (command.action()) {
            case APPROVE_R1 -> exact(current, FeatureStage.WAIT_R1_APPROVAL,
                    mutation(job, FeatureStage.DESIGN, 0, command.reason()));
            case APPROVE_R2 -> exact(current, FeatureStage.WAIT_R2_APPROVAL,
                    mutation(job, FeatureStage.IMPLEMENT_RED, 0, command.reason()));
            case APPROVE_R3 -> exact(current, FeatureStage.WAIT_R3_APPROVAL,
                    mutation(job, FeatureStage.SHIP, 0, command.reason()));
            case REJECT_R1 -> reject(job, FeatureStage.WAIT_R1_APPROVAL, FeatureStage.SPECIFY, command.reason());
            case REJECT_R2 -> reject(job, FeatureStage.WAIT_R2_APPROVAL, FeatureStage.DESIGN, command.reason());
            case REJECT_R3 -> reject(job, FeatureStage.WAIT_R3_APPROVAL, FeatureStage.IMPLEMENT_RED,
                    command.reason());
            case PAUSE -> pause(job, command.reason());
            case RESUME -> resume(job, command.reason());
            case CANCEL -> cancel(job, command.reason());
            case STATUS -> Optional.empty();
        };
    }

    private static Optional<FeatureJobMutation> exact(FeatureStage current, FeatureStage expected,
                                                      FeatureJobMutation mutation) {
        return current == expected ? Optional.of(mutation) : Optional.empty();
    }

    private static Optional<FeatureJobMutation> reject(FeatureJob job, FeatureStage expected,
                                                       FeatureStage target, String reason) {
        int nextRound = job.progress().gateRound() + 1;
        if (job.progress().stage() != expected || nextRound > 3) {
            return Optional.empty();
        }
        return Optional.of(mutation(job, target, nextRound, "rejected: " + reason));
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
        boolean resumable = current == FeatureStage.PAUSED
                || current == FeatureStage.WAITING_DEPENDENCY_PREFETCH
                || current == FeatureStage.WAITING_HUMAN;
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
        FeatureJob.Lease lease = new FeatureJob.Lease(
                result.getString("lease_owner"), result.getLong("lease_until"));
        FeatureJob.RecordMetadata metadata = new FeatureJob.RecordMetadata(
                result.getLong("version"), result.getString("last_error"), result.getLong("created_at"),
                result.getLong("updated_at"));
        return new FeatureJob(identity, progress, pullRequest, lease, metadata);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static FeatureStage optionalStage(String value) {
        return value == null || value.isBlank() ? null : FeatureStage.valueOf(value);
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

    private static void requireOne(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
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
