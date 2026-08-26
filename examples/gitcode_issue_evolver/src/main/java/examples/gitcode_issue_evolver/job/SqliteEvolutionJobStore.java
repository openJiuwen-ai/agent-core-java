/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import examples.gitcode_issue_evolver.curation.CodingStandardCurationTask;
import examples.gitcode_issue_evolver.curation.CodingStandardFindingEvidence;
import examples.gitcode_issue_evolver.curation.CodingStandardLesson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * SQLite implementation with delivery deduplication, optimistic locking, and worker leases.
 *
 * @since 0.1.12
 */
public final class SqliteEvolutionJobStore implements EvolutionJobStore {
    private static final int MAX_DETAIL_LENGTH = 4000;
    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteEvolutionJobStore.class);
    private static final String ACTIVE_STATES = "'RECEIVED','PLANNING','IMPLEMENTING','VERIFYING',"
            + "'SMOKE_TESTING','COMMITTED','PUBLISHING','PR_CREATED','WAITING_REVIEW','CODECHECK_REPAIR',"
            + "'RETRY_SCHEDULED',"
            + "'CANCEL_REQUESTED'";
    private static final String JOB_COLUMNS = "id,repo,issue_iid,issue_title,issue_url,state,"
            + "trigger_delivery_id,branch,head_sha,pr_number,pr_url,draft,attempt_count,next_attempt_at,"
            + "primary_repair_rounds,diagnostic_repair_rounds,last_failure_code,last_failure_category,"
            + "lease_owner,lease_until,version,last_error,created_at,updated_at";
    private static final String FIND_BY_PULL_REQUEST_SQL = "SELECT " + JOB_COLUMNS
            + " FROM evolution_jobs WHERE repo=? AND pr_number=? ORDER BY created_at DESC LIMIT 1";
    private static final String FIND_ACTIVE_ISSUE_SQL = "SELECT " + JOB_COLUMNS
            + " FROM evolution_jobs WHERE repo=? AND issue_iid=? AND state IN (" + ACTIVE_STATES + ")"
            + " ORDER BY created_at DESC LIMIT 1";
    private static final String FIND_LATEST_ISSUE_SQL = "SELECT " + JOB_COLUMNS
            + " FROM evolution_jobs WHERE repo=? AND issue_iid=? ORDER BY created_at DESC LIMIT 1";
    private static final String FIND_BY_ID_SQL = "SELECT " + JOB_COLUMNS + " FROM evolution_jobs WHERE id=?";
    private static final String LIST_REVIEW_JOBS_SQL = "SELECT " + JOB_COLUMNS
            + " FROM evolution_jobs WHERE state='WAITING_REVIEW' AND pr_number IS NOT NULL"
            + " ORDER BY pr_checked_at,updated_at LIMIT ?";
    private static final String CLAIM_JOB_SQL = "UPDATE evolution_jobs SET lease_owner=?,lease_until=?,"
            + "attempt_count=attempt_count+1,version=version+1,updated_at=? WHERE id=(SELECT id "
            + "FROM evolution_jobs WHERE state IN ('RECEIVED','CODECHECK_REPAIR','RETRY_SCHEDULED',"
            + "'CANCEL_REQUESTED') "
            + "AND next_attempt_at<=? "
            + "AND (lease_until=0 OR lease_until<?) ORDER BY created_at LIMIT 1) "
            + "AND (lease_until=0 OR lease_until<?) RETURNING " + JOB_COLUMNS;
    private final String jdbcUrl;

    /**
     * Open or create a versioned SQLite job database.
     *
     * @param databasePath database file path
     */
    public SqliteEvolutionJobStore(Path databasePath) {
        Path normalized = Objects.requireNonNull(databasePath, "databasePath must not be null")
                .toAbsolutePath().normalize();
        try {
            if (normalized.getParent() != null) {
                Files.createDirectories(normalized.getParent());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create SQLite data directory", ex);
        }
        this.jdbcUrl = "jdbc:sqlite:" + normalized;
        initialize();
    }

    @Override
    public boolean acceptDelivery(String deliveryId, String eventType, String payloadSha256) {
        requireText(deliveryId, "deliveryId");
        requireText(eventType, "eventType");
        requireText(payloadSha256, "payloadSha256");
        try (Connection connection = connection()) {
            return insertDelivery(connection, deliveryId, eventType, payloadSha256) == 1;
        } catch (SQLException ex) {
            throw failure("accept webhook delivery", ex);
        }
    }

    @Override
    public EnqueueResult enqueueIssue(IssueJobRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                int inserted = insertDelivery(
                        connection, request.deliveryId(), request.eventType(), request.payloadSha256());
                if (inserted == 0) {
                    connection.commit();
                    return new EnqueueResult(EnqueueResult.Status.DUPLICATE_DELIVERY,
                            findByIssue(connection, request.repository(), request.issueIid(), true));
                }
                int admitted = insertIssueAdmission(connection, request);
                if (admitted == 0) {
                    Optional<EvolutionJob> existing = findAnyByIssue(
                            connection, request.repository(), request.issueIid());
                    connection.commit();
                    EnqueueResult.Status status = existing.filter(job -> job.state().isActive()).isPresent()
                            ? EnqueueResult.Status.EXISTING_ACTIVE_JOB : EnqueueResult.Status.EXISTING_ISSUE;
                    return new EnqueueResult(status, existing);
                }
                EvolutionJob created = insertJob(connection, request);
                connection.commit();
                return new EnqueueResult(EnqueueResult.Status.CREATED, Optional.of(created));
            } catch (SQLException | IllegalArgumentException | IllegalStateException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw failure("enqueue issue", ex);
        }
    }

    @Override
    public Optional<EvolutionJob> createJobIfAbsent(IssueJobRequest request) {
        return enqueueIssue(request).job();
    }

    @Override
    public Optional<EvolutionJob> findByIssue(String repository, long issueIid) {
        requireText(repository, "repository");
        requirePositive(issueIid, "issueIid");
        try (Connection connection = connection()) {
            Optional<EvolutionJob> active = findByIssue(connection, repository, issueIid, true);
            return active.isPresent() ? active : findByIssue(connection, repository, issueIid, false);
        } catch (SQLException ex) {
            throw failure("find issue job", ex);
        }
    }

    @Override
    public Optional<EvolutionJob> findById(String jobId) {
        requireText(jobId, "jobId");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
            statement.setString(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readJob(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw failure("find evolution job", ex);
        }
    }

    @Override
    public Optional<EvolutionJob> findByPullRequest(String repository, long pullRequestNumber) {
        requireText(repository, "repository");
        requirePositive(pullRequestNumber, "pullRequestNumber");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_PULL_REQUEST_SQL)) {
            statement.setString(1, repository);
            statement.setLong(2, pullRequestNumber);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readJob(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw failure("find pull request job", ex);
        }
    }

    @Override
    public Optional<IssueScanCheckpoint> loadIssueScanCheckpoint(String repository, String label) {
        requireText(repository, "repository");
        requireText(label, "label");
        String sql = "SELECT window_start,window_end,next_page FROM issue_scan_checkpoints "
                + "WHERE repo=? AND label=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, repository);
            statement.setString(2, label);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new IssueScanCheckpoint(repository, label,
                        Instant.ofEpochMilli(result.getLong("window_start")),
                        Instant.ofEpochMilli(result.getLong("window_end")), result.getInt("next_page")));
            }
        } catch (SQLException ex) {
            throw failure("load Issue scan checkpoint", ex);
        }
    }

    @Override
    public void saveIssueScanCheckpoint(IssueScanCheckpoint checkpoint) {
        IssueScanCheckpoint required = Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        String sql = "INSERT INTO issue_scan_checkpoints(repo,label,window_start,window_end,next_page,updated_at) "
                + "VALUES(?,?,?,?,?,?) ON CONFLICT(repo,label) DO UPDATE SET window_start=excluded.window_start,"
                + "window_end=excluded.window_end,next_page=excluded.next_page,updated_at=excluded.updated_at";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, required.repository());
            statement.setString(2, required.label());
            statement.setLong(3, required.windowStart().toEpochMilli());
            statement.setLong(4, required.windowEnd().toEpochMilli());
            statement.setInt(5, required.nextPage());
            statement.setLong(6, System.currentTimeMillis());
            requireUpdated(statement.executeUpdate(), required.repository() + ":" + required.label());
        } catch (SQLException ex) {
            throw failure("save Issue scan checkpoint", ex);
        }
    }

    @Override
    public void clearIssueScanCheckpoint(String repository, String label) {
        requireText(repository, "repository");
        requireText(label, "label");
        String sql = "DELETE FROM issue_scan_checkpoints WHERE repo=? AND label=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, repository);
            statement.setString(2, label);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("clear Issue scan checkpoint", ex);
        }
    }

    @Override
    public List<EvolutionJob> listPullRequestsForReconciliation(int limit) {
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000");
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(LIST_REVIEW_JOBS_SQL)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<EvolutionJob> jobs = new ArrayList<>();
                while (result.next()) {
                    jobs.add(readJob(result));
                }
                return List.copyOf(jobs);
            }
        } catch (SQLException ex) {
            throw failure("list review-waiting jobs", ex);
        }
    }

    @Override
    public void markPullRequestChecked(String jobId, long checkedAt) {
        requireText(jobId, "jobId");
        requireNonNegative(checkedAt, "checkedAt");
        String sql = "UPDATE evolution_jobs SET pr_checked_at=? WHERE id=? AND state='WAITING_REVIEW'";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, checkedAt);
            statement.setString(2, jobId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("mark pull request checked", ex);
        }
    }

    @Override
    public Optional<EvolutionJob> scheduleCodeCheckRepair(CodeCheckRepairRequest request) {
        CodeCheckRepairRequest required = Objects.requireNonNull(request, "request must not be null");
        requireText(required.jobId(), "jobId");
        requireText(required.fingerprint(), "fingerprint");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (!insertCodeCheckFeedback(connection, required)) {
                    connection.commit();
                    return Optional.empty();
                }
                EvolutionJob before = requireById(connection, required.jobId());
                if (before.state() != EvolutionJobState.WAITING_REVIEW) {
                    throw new IllegalStateException("CodeCheck repair requires WAITING_REVIEW state");
                }
                updateCodeCheckRepair(connection, required);
                insertCodeCheckFailure(connection, required);
                insertCodingStandardCuration(connection, required);
                EvolutionJob after = requireById(connection, required.jobId());
                appendEvent(connection, required.jobId(), before.state(), after.state(), required.summary());
                connection.commit();
                return Optional.of(after);
            } catch (SQLException | IllegalArgumentException | IllegalStateException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw failure("schedule CodeCheck repair", ex);
        }
    }

    private static boolean insertCodeCheckFeedback(Connection connection, CodeCheckRepairRequest request)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO issue_codecheck_feedback"
                + "(job_id,fingerprint,comment_id,report_url,head_sha,created_at) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.jobId());
            statement.setString(2, safeDetail(request.fingerprint()));
            statement.setString(3, safeDetail(request.commentId()));
            statement.setString(4, safeDetail(request.reportUrl()));
            statement.setString(5, safeDetail(request.headSha()));
            statement.setLong(6, System.currentTimeMillis());
            return statement.executeUpdate() == 1;
        }
    }

    private static void updateCodeCheckRepair(Connection connection, CodeCheckRepairRequest request)
            throws SQLException {
        String sql = "UPDATE evolution_jobs SET state='CODECHECK_REPAIR',primary_repair_rounds=0,"
                + "diagnostic_repair_rounds=0,last_failure_code='CODECHECK_FAILED',"
                + "last_failure_category='AGENT_CORRECTABLE',last_error=?,pr_checked_at=?,"
                + "next_attempt_at=0,lease_owner='',lease_until=0,version=version+1,updated_at=? "
                + "WHERE id=? AND state='WAITING_REVIEW' AND version=?";
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeDetail(request.summary()));
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, request.jobId());
            statement.setLong(5, request.expectedVersion());
            requireUpdated(statement.executeUpdate(), request.jobId());
        }
    }

    private static void insertCodeCheckFailure(Connection connection, CodeCheckRepairRequest request)
            throws SQLException {
        String sql = "INSERT INTO issue_failure_events"
                + "(job_id,stage,code,category,summary,diagnostic,created_at) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.jobId());
            statement.setString(2, "CODECHECK");
            statement.setString(3, "CODECHECK_FAILED");
            statement.setString(4, IssueFailureCategory.AGENT_CORRECTABLE.name());
            statement.setString(5, safeDetail(request.summary()));
            statement.setString(6, safeDetail(request.diagnostic()));
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static void insertCodingStandardCuration(Connection connection,
                                                     CodeCheckRepairRequest request)
            throws SQLException {
        if (request.curationFindings().isEmpty()) {
            return;
        }
        String taskSql = "INSERT OR IGNORE INTO coding_standard_curation_tasks"
                + "(job_id,feedback_fingerprint,status,attempt_count,next_attempt_at,last_error,"
                + "created_at,updated_at) VALUES(?,?,'PENDING',0,0,'',?,?)";
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(taskSql)) {
            statement.setString(1, request.jobId());
            statement.setString(2, safeDetail(request.fingerprint()));
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
        insertCodingStandardFindings(connection, request);
    }

    private static void insertCodingStandardFindings(Connection connection,
                                                      CodeCheckRepairRequest request)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO coding_standard_curation_findings"
                + "(job_id,feedback_fingerprint,ordinal,rule_id,rule_name,description,level) "
                + "VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int ordinal = 0;
            for (CodingStandardFindingEvidence finding : request.curationFindings()) {
                statement.setString(1, request.jobId());
                statement.setString(2, safeDetail(request.fingerprint()));
                statement.setInt(3, ordinal++);
                statement.setString(4, safeDetail(finding.ruleId()));
                statement.setString(5, safeDetail(finding.ruleName()));
                statement.setString(6, safeDetail(finding.description()));
                statement.setString(7, safeDetail(finding.level()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public Optional<CodingStandardCurationTask> nextCodingStandardCurationTask() {
        String sql = "SELECT task.job_id,task.feedback_fingerprint,task.attempt_count "
                + "FROM coding_standard_curation_tasks task JOIN evolution_jobs job ON job.id=task.job_id "
                + "WHERE task.status='PENDING' AND task.next_attempt_at<=? AND job.state='MERGED' "
                + "ORDER BY task.created_at LIMIT 1";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String jobId = result.getString("job_id");
                String fingerprint = result.getString("feedback_fingerprint");
                int attempts = result.getInt("attempt_count");
                return Optional.of(new CodingStandardCurationTask(jobId, fingerprint, attempts,
                        readCodingStandardFindings(connection, jobId, fingerprint)));
            }
        } catch (SQLException ex) {
            throw failure("find coding-standard curation task", ex);
        }
    }

    private static List<CodingStandardFindingEvidence> readCodingStandardFindings(
            Connection connection, String jobId, String fingerprint) throws SQLException {
        String sql = "SELECT rule_id,rule_name,description,level "
                + "FROM coding_standard_curation_findings WHERE job_id=? AND feedback_fingerprint=? "
                + "ORDER BY ordinal";
        List<CodingStandardFindingEvidence> findings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setString(2, fingerprint);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    findings.add(new CodingStandardFindingEvidence(
                            result.getString("rule_id"), result.getString("rule_name"),
                            result.getString("description"), result.getString("level")));
                }
            }
        }
        return List.copyOf(findings);
    }

    @Override
    public void completeCodingStandardCuration(CodingStandardCurationTask task,
                                               List<CodingStandardLesson> lessons) {
        CodingStandardCurationTask requiredTask = Objects.requireNonNull(task, "task must not be null");
        List<CodingStandardLesson> requiredLessons = List.copyOf(
                Objects.requireNonNull(lessons, "lessons must not be null"));
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                insertCodingStandardLessons(connection, requiredTask, requiredLessons);
                completeCodingStandardTask(connection, requiredTask, requiredLessons.isEmpty());
                connection.commit();
            } catch (SQLException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw failure("complete coding-standard curation", ex);
        }
    }

    private static void insertCodingStandardLessons(Connection connection,
                                                    CodingStandardCurationTask task,
                                                    List<CodingStandardLesson> lessons)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO coding_standard_lessons"
                + "(fingerprint,rule_id,category,summary,prevention,source_job_id,"
                + "source_feedback_fingerprint,created_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CodingStandardLesson lesson : lessons) {
                statement.setString(1, lesson.fingerprint());
                statement.setString(2, lesson.ruleId());
                statement.setString(3, lesson.category());
                statement.setString(4, safeDetail(lesson.summary()));
                statement.setString(5, safeDetail(lesson.prevention()));
                statement.setString(6, task.jobId());
                statement.setString(7, task.feedbackFingerprint());
                statement.setLong(8, System.currentTimeMillis());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void completeCodingStandardTask(Connection connection,
                                                   CodingStandardCurationTask task,
                                                   boolean hasNoUpdate) throws SQLException {
        String sql = "UPDATE coding_standard_curation_tasks SET status=?,last_error='',updated_at=? "
                + "WHERE job_id=? AND feedback_fingerprint=? AND status='PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hasNoUpdate ? "NO_UPDATE" : "COMPLETED");
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, task.jobId());
            statement.setString(4, task.feedbackFingerprint());
            statement.executeUpdate();
        }
    }

    @Override
    public void failCodingStandardCuration(CodingStandardCurationTask task, String error,
                                           int maximumAttempts) {
        CodingStandardCurationTask requiredTask = Objects.requireNonNull(task, "task must not be null");
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts must be positive");
        }
        int attempts = requiredTask.attemptCount() + 1;
        boolean isExhausted = attempts >= maximumAttempts;
        long nextAttemptAt = isExhausted ? 0 : System.currentTimeMillis() + retryDelayMillis(attempts);
        String sql = "UPDATE coding_standard_curation_tasks SET status=?,attempt_count=?,"
                + "next_attempt_at=?,last_error=?,updated_at=? "
                + "WHERE job_id=? AND feedback_fingerprint=? AND status='PENDING'";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isExhausted ? "FAILED" : "PENDING");
            statement.setInt(2, attempts);
            statement.setLong(3, nextAttemptAt);
            statement.setString(4, safeDetail(error));
            statement.setLong(5, System.currentTimeMillis());
            statement.setString(6, requiredTask.jobId());
            statement.setString(7, requiredTask.feedbackFingerprint());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("record coding-standard curation failure", ex);
        }
    }

    private static long retryDelayMillis(int attempts) {
        return switch (attempts) {
            case 1 -> Duration.ofSeconds(30).toMillis();
            case 2 -> Duration.ofMinutes(2).toMillis();
            default -> Duration.ofMinutes(10).toMillis();
        };
    }

    @Override
    public List<CodingStandardLesson> listCodingStandardLessons(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        String sql = "SELECT fingerprint,rule_id,category,summary,prevention "
                + "FROM coding_standard_lessons ORDER BY created_at DESC LIMIT ?";
        List<CodingStandardLesson> lessons = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    lessons.add(new CodingStandardLesson(
                            result.getString("fingerprint"), result.getString("rule_id"),
                            result.getString("category"), result.getString("summary"),
                            result.getString("prevention")));
                }
            }
            return List.copyOf(lessons);
        } catch (SQLException ex) {
            throw failure("list coding-standard lessons", ex);
        }
    }

    @Override
    public void recordFailureEvent(String jobId, String stage, String code,
                                   IssueFailureCategory category, String summary,
                                   String diagnostic) {
        requireText(jobId, "jobId");
        requireText(stage, "stage");
        requireText(code, "code");
        Objects.requireNonNull(category, "category must not be null");
        String sql = "INSERT INTO issue_failure_events"
                + "(job_id,stage,code,category,summary,diagnostic,created_at) VALUES(?,?,?,?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setString(2, safeDetail(stage));
            statement.setString(3, safeDetail(code));
            statement.setString(4, category.name());
            statement.setString(5, safeDetail(summary));
            statement.setString(6, safeDetail(diagnostic));
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("record Issue failure event", ex);
        }
    }

    @Override
    public void recordGateReceipt(String jobId, String fingerprint, String status,
                                  String profile, String code, String category,
                                  boolean cached, int exitCode, String outputTail,
                                  long completedAt) {
        requireText(jobId, "jobId");
        requireText(fingerprint, "fingerprint");
        requireText(status, "status");
        requireText(profile, "profile");
        requireNonNegative(completedAt, "completedAt");
        String sql = "INSERT INTO issue_gate_receipts"
                + "(job_id,fingerprint,status,profile,code,category,cached,exit_code,output_tail,completed_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(job_id,fingerprint) DO UPDATE SET "
                + "status=excluded.status,profile=excluded.profile,code=excluded.code,"
                + "category=excluded.category,cached=excluded.cached,exit_code=excluded.exit_code,"
                + "output_tail=excluded.output_tail,completed_at=excluded.completed_at";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setString(2, fingerprint);
            statement.setString(3, status);
            statement.setString(4, profile);
            statement.setString(5, safeDetail(code));
            statement.setString(6, safeDetail(category));
            statement.setInt(7, cached ? 1 : 0);
            statement.setInt(8, exitCode);
            statement.setString(9, safeDetail(outputTail));
            statement.setLong(10, completedAt);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failure("record Issue Gate receipt", ex);
        }
    }

    @Override
    public Optional<IssueGateReceipt> findGateReceipt(String jobId, String fingerprint) {
        requireText(jobId, "jobId");
        requireText(fingerprint, "fingerprint");
        String sql = "SELECT status,profile,code,category,cached,exit_code,output_tail,completed_at "
                + "FROM issue_gate_receipts WHERE job_id=? AND fingerprint=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setString(2, fingerprint);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new IssueGateReceipt(fingerprint,
                        result.getString("status"), result.getString("profile"),
                        result.getString("code"), result.getString("category"),
                        result.getInt("cached") == 1, result.getInt("exit_code"),
                        result.getString("output_tail"), result.getLong("completed_at")));
            }
        } catch (SQLException ex) {
            throw failure("find Issue Gate receipt", ex);
        }
    }

    @Override
    public void recordRepairProgress(String jobId, int primaryRounds, int diagnosticRounds,
                                     String failureCode, String failureCategory) {
        requireText(jobId, "jobId");
        requireNonNegative(primaryRounds, "primaryRounds");
        requireNonNegative(diagnosticRounds, "diagnosticRounds");
        String sql = "UPDATE evolution_jobs SET primary_repair_rounds=?,"
                + "diagnostic_repair_rounds=?,last_failure_code=?,last_failure_category=?,"
                + "updated_at=? WHERE id=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, primaryRounds);
            statement.setInt(2, diagnosticRounds);
            statement.setString(3, safeDetail(failureCode));
            statement.setString(4, safeDetail(failureCategory));
            statement.setLong(5, System.currentTimeMillis());
            statement.setString(6, jobId);
            requireUpdated(statement.executeUpdate(), jobId);
        } catch (SQLException ex) {
            throw failure("record Issue repair progress", ex);
        }
    }

    @Override
    public List<String> recentFailureContext(String jobId, int limit) {
        requireText(jobId, "jobId");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        String sql = "SELECT code,category,summary,diagnostic FROM issue_failure_events "
                + "WHERE job_id=? ORDER BY id DESC LIMIT ?";
        List<String> context = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    context.add("code=" + result.getString("code")
                            + ", category=" + result.getString("category")
                            + ", summary=" + result.getString("summary")
                            + ", diagnostic=" + result.getString("diagnostic"));
                }
            }
            return List.copyOf(context);
        } catch (SQLException ex) {
            throw failure("load Issue failure context", ex);
        }
    }

    @Override
    public Optional<EvolutionJob> leaseNext(String workerId, Duration leaseDuration) {
        requireText(workerId, "workerId");
        long now = System.currentTimeMillis();
        long leaseUntil = leaseDeadline(now, leaseDuration);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(CLAIM_JOB_SQL)) {
                statement.setString(1, workerId);
                statement.setLong(2, leaseUntil);
                statement.setLong(3, now);
                statement.setLong(4, now);
                statement.setLong(5, now);
                statement.setLong(6, now);
                EvolutionJob leased;
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        connection.commit();
                        return Optional.empty();
                    }
                    leased = readJob(result);
                }
                appendEvent(connection, leased.id(), leased.state(), leased.state(), "leased by " + workerId);
                connection.commit();
                return Optional.of(leased);
            } catch (SQLException | IllegalArgumentException | IllegalStateException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw failure("lease issue job", ex);
        }
    }

    @Override
    public boolean heartbeat(String jobId, String workerId, Duration leaseDuration) {
        requireText(jobId, "jobId");
        requireText(workerId, "workerId");
        String sql = "UPDATE evolution_jobs SET lease_until=?,updated_at=? "
                + "WHERE id=? AND lease_owner=? AND lease_until>0";
        long now = System.currentTimeMillis();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, leaseDeadline(now, leaseDuration));
            statement.setLong(2, now);
            statement.setString(3, jobId);
            statement.setString(4, workerId);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw failure("heartbeat issue job", ex);
        }
    }

    @Override
    public EvolutionJob transition(String jobId, long expectedVersion, EvolutionJobState state, String error) {
        requireText(jobId, "jobId");
        requireNonNegative(expectedVersion, "expectedVersion");
        Objects.requireNonNull(state, "state must not be null");
        String safeError = safeDetail(error);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                lockJobForWrite(connection, jobId);
                EvolutionJob before = requireById(connection, jobId);
                requireTransition(before.state(), state);
                boolean retryScheduled = state == EvolutionJobState.RETRY_SCHEDULED;
                long nextAttemptAt = retryScheduled
                        ? System.currentTimeMillis() + retryDelay(before.attemptCount()) : 0L;
                boolean release = state.releasesLease() || retryScheduled;
                String sql = "UPDATE evolution_jobs SET state=?,last_error=?,next_attempt_at=?,"
                        + (release ? "lease_owner='',lease_until=0," : "")
                        + "version=version+1,updated_at=? WHERE id=? AND version=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, state.name());
                    statement.setString(2, safeError);
                    statement.setLong(3, nextAttemptAt);
                    statement.setLong(4, System.currentTimeMillis());
                    statement.setString(5, jobId);
                    statement.setLong(6, expectedVersion);
                    requireUpdated(statement.executeUpdate(), jobId);
                }
                EvolutionJob after = requireById(connection, jobId);
                appendEvent(connection, jobId, before.state(), state, safeError);
                connection.commit();
                return after;
            } catch (SQLException | IllegalArgumentException | IllegalStateException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw failure("transition issue job", ex);
        }
    }

    @Override
    public EvolutionJob requestCancellation(String jobId, String reason) {
        requireText(jobId, "jobId");
        String safeReason = safeDetail(reason);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                lockJobForWrite(connection, jobId);
                EvolutionJob before = requireById(connection, jobId);
                if (before.state() == EvolutionJobState.CANCEL_REQUESTED
                        || before.state() == EvolutionJobState.CANCELLED) {
                    connection.commit();
                    return before;
                }
                requireTransition(before.state(), EvolutionJobState.CANCEL_REQUESTED);
                String sql = "UPDATE evolution_jobs SET state='CANCEL_REQUESTED',last_error=?,"
                        + "next_attempt_at=0,version=version+1,updated_at=? WHERE id=? AND version=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, safeReason);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.setString(3, jobId);
                    statement.setLong(4, before.version());
                    requireUpdated(statement.executeUpdate(), jobId);
                }
                EvolutionJob after = requireById(connection, jobId);
                appendEvent(connection, jobId, before.state(), after.state(), safeReason);
                connection.commit();
                return after;
            } catch (SQLException | IllegalArgumentException | IllegalStateException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw failure("request job cancellation", ex);
        }
    }

    @Override
    public EvolutionJob recordPullRequest(String jobId, long expectedVersion, long number,
                                          String url, String headSha, boolean draft) {
        requireText(jobId, "jobId");
        requireNonNegative(expectedVersion, "expectedVersion");
        requirePositive(number, "pullRequestNumber");
        String requiredUrl = requireText(url, "url");
        String requiredHeadSha = requireText(headSha, "headSha");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                lockJobForWrite(connection, jobId);
                EvolutionJob before = requireById(connection, jobId);
                requireTransition(before.state(), EvolutionJobState.PR_CREATED);
                String sql = "UPDATE evolution_jobs SET state='PR_CREATED',pr_number=?,pr_url=?,head_sha=?,draft=?,"
                        + "version=version+1,updated_at=? WHERE id=? AND version=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, number);
                    statement.setString(2, requiredUrl);
                    statement.setString(3, requiredHeadSha);
                    statement.setInt(4, draft ? 1 : 0);
                    statement.setLong(5, System.currentTimeMillis());
                    statement.setString(6, jobId);
                    statement.setLong(7, expectedVersion);
                    requireUpdated(statement.executeUpdate(), jobId);
                }
                EvolutionJob after = requireById(connection, jobId);
                appendEvent(connection, jobId, before.state(), after.state(), requiredUrl);
                connection.commit();
                return after;
            } catch (SQLException | IllegalArgumentException | IllegalStateException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw failure("record pull request", ex);
        }
    }

    @Override
    public void recoverExpiredLeases() {
        long now = System.currentTimeMillis();
        recoverExpiredCancellationLeases(now);
        String sql = "UPDATE evolution_jobs SET state='RETRY_SCHEDULED',lease_owner='',lease_until=0,"
                + "next_attempt_at=? + CASE attempt_count "
                + "WHEN 1 THEN 30000 WHEN 2 THEN 120000 ELSE 600000 END,"
                + "last_error='WORKER_INFRASTRUCTURE_FAILED: worker lease expired',"
                + "version=version+1,updated_at=? "
                + "WHERE state IN (" + ACTIVE_STATES + ") "
                + "AND state NOT IN ('RECEIVED','RETRY_SCHEDULED',"
                + "'WAITING_REVIEW','CANCEL_REQUESTED') "
                + "AND lease_until>0 AND lease_until<?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setLong(3, now);
            int recovered = statement.executeUpdate();
            if (recovered > 0) {
                LOGGER.info("Recovered {} expired evolution job leases", recovered);
            }
        } catch (SQLException ex) {
            throw failure("recover expired leases", ex);
        }
    }

    private void recoverExpiredCancellationLeases(long now) {
        String sql = "UPDATE evolution_jobs SET lease_owner='',lease_until=0,next_attempt_at=0,"
                + "version=version+1,updated_at=? WHERE state='CANCEL_REQUESTED' "
                + "AND lease_until>0 AND lease_until<?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            int recovered = statement.executeUpdate();
            if (recovered > 0) {
                LOGGER.info("Recovered {} expired cancellation leases", recovered);
            }
        } catch (SQLException ex) {
            throw failure("recover expired cancellation leases", ex);
        }
    }

    private void initialize() {
        String schema = """
                CREATE TABLE IF NOT EXISTS webhook_deliveries (
                  delivery_id TEXT PRIMARY KEY,
                  event_type TEXT NOT NULL,
                  payload_sha256 TEXT NOT NULL,
                  received_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS evolution_jobs (
                  id TEXT PRIMARY KEY,
                  repo TEXT NOT NULL,
                  issue_iid INTEGER NOT NULL,
                  issue_title TEXT NOT NULL,
                  issue_url TEXT NOT NULL,
                  state TEXT NOT NULL,
                  trigger_delivery_id TEXT NOT NULL UNIQUE,
                  branch TEXT NOT NULL,
                  head_sha TEXT NOT NULL DEFAULT '',
                  pr_number INTEGER,
                  pr_url TEXT NOT NULL DEFAULT '',
                  draft INTEGER NOT NULL DEFAULT 0,
                  attempt_count INTEGER NOT NULL DEFAULT 0,
                  primary_repair_rounds INTEGER NOT NULL DEFAULT 0,
                  diagnostic_repair_rounds INTEGER NOT NULL DEFAULT 0,
                  last_failure_code TEXT NOT NULL DEFAULT '',
                  last_failure_category TEXT NOT NULL DEFAULT '',
                  next_attempt_at INTEGER NOT NULL DEFAULT 0,
                  lease_owner TEXT NOT NULL DEFAULT '',
                  lease_until INTEGER NOT NULL DEFAULT 0,
                  version INTEGER NOT NULL DEFAULT 0,
                  last_error TEXT NOT NULL DEFAULT '',
                  pr_checked_at INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  FOREIGN KEY(trigger_delivery_id) REFERENCES webhook_deliveries(delivery_id)
                );
                CREATE TABLE IF NOT EXISTS evolution_job_events (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  job_id TEXT NOT NULL,
                  from_state TEXT NOT NULL,
                  to_state TEXT NOT NULL,
                  detail TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  FOREIGN KEY(job_id) REFERENCES evolution_jobs(id)
                );
                CREATE TABLE IF NOT EXISTS issue_admissions (
                  repo TEXT NOT NULL,
                  issue_iid INTEGER NOT NULL,
                  first_delivery_id TEXT NOT NULL,
                  admitted_at INTEGER NOT NULL,
                  PRIMARY KEY(repo,issue_iid),
                  FOREIGN KEY(first_delivery_id) REFERENCES webhook_deliveries(delivery_id)
                );
                CREATE TABLE IF NOT EXISTS issue_scan_checkpoints (
                  repo TEXT NOT NULL,
                  label TEXT NOT NULL,
                  window_start INTEGER NOT NULL,
                  window_end INTEGER NOT NULL,
                  next_page INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  PRIMARY KEY(repo,label)
                );
                CREATE TABLE IF NOT EXISTS issue_failure_events (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  job_id TEXT NOT NULL,
                  stage TEXT NOT NULL,
                  code TEXT NOT NULL,
                  category TEXT NOT NULL,
                  summary TEXT NOT NULL,
                  diagnostic TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  FOREIGN KEY(job_id) REFERENCES evolution_jobs(id)
                );
                CREATE TABLE IF NOT EXISTS issue_gate_receipts (
                  job_id TEXT NOT NULL,
                  fingerprint TEXT NOT NULL,
                  status TEXT NOT NULL,
                  profile TEXT NOT NULL,
                  code TEXT NOT NULL,
                  category TEXT NOT NULL,
                  cached INTEGER NOT NULL,
                  exit_code INTEGER NOT NULL,
                  output_tail TEXT NOT NULL,
                  completed_at INTEGER NOT NULL,
                  PRIMARY KEY(job_id,fingerprint),
                  FOREIGN KEY(job_id) REFERENCES evolution_jobs(id)
                );
                CREATE TABLE IF NOT EXISTS issue_codecheck_feedback (
                  job_id TEXT NOT NULL,
                  fingerprint TEXT NOT NULL,
                  comment_id TEXT NOT NULL,
                  report_url TEXT NOT NULL,
                  head_sha TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  PRIMARY KEY(job_id,fingerprint),
                  FOREIGN KEY(job_id) REFERENCES evolution_jobs(id)
                );
                CREATE TABLE IF NOT EXISTS coding_standard_curation_tasks (
                  job_id TEXT NOT NULL,
                  feedback_fingerprint TEXT NOT NULL,
                  status TEXT NOT NULL,
                  attempt_count INTEGER NOT NULL,
                  next_attempt_at INTEGER NOT NULL,
                  last_error TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  PRIMARY KEY(job_id,feedback_fingerprint),
                  FOREIGN KEY(job_id) REFERENCES evolution_jobs(id)
                );
                CREATE TABLE IF NOT EXISTS coding_standard_curation_findings (
                  job_id TEXT NOT NULL,
                  feedback_fingerprint TEXT NOT NULL,
                  ordinal INTEGER NOT NULL,
                  rule_id TEXT NOT NULL,
                  rule_name TEXT NOT NULL,
                  description TEXT NOT NULL,
                  level TEXT NOT NULL,
                  PRIMARY KEY(job_id,feedback_fingerprint,ordinal),
                  FOREIGN KEY(job_id,feedback_fingerprint)
                    REFERENCES coding_standard_curation_tasks(job_id,feedback_fingerprint)
                );
                CREATE TABLE IF NOT EXISTS coding_standard_lessons (
                  fingerprint TEXT PRIMARY KEY,
                  rule_id TEXT NOT NULL,
                  category TEXT NOT NULL,
                  summary TEXT NOT NULL,
                  prevention TEXT NOT NULL,
                  source_job_id TEXT NOT NULL,
                  source_feedback_fingerprint TEXT NOT NULL,
                  created_at INTEGER NOT NULL,
                  FOREIGN KEY(source_job_id,source_feedback_fingerprint)
                    REFERENCES coding_standard_curation_tasks(job_id,feedback_fingerprint)
                );
                DROP INDEX IF EXISTS ux_evolution_active_issue;
                CREATE UNIQUE INDEX ux_evolution_active_issue
                  ON evolution_jobs(repo, issue_iid) WHERE state IN (
                    'RECEIVED','PLANNING','IMPLEMENTING','VERIFYING','SMOKE_TESTING','COMMITTED','PUBLISHING',
                    'PR_CREATED','WAITING_REVIEW','CODECHECK_REPAIR','RETRY_SCHEDULED','CANCEL_REQUESTED');
                CREATE UNIQUE INDEX IF NOT EXISTS ux_evolution_pr
                  ON evolution_jobs(repo, pr_number) WHERE pr_number IS NOT NULL;
                """;
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            executePragma(statement, "PRAGMA journal_mode=WAL");
            for (String sql : schema.split(";")) {
                if (!sql.isBlank()) {
                    int updated = statement.executeUpdate(sql);
                    LOGGER.debug("Applied SQLite schema statement with update count {}", updated);
                }
            }
            ensureColumn(connection, "evolution_jobs", "pr_checked_at",
                    "ALTER TABLE evolution_jobs ADD COLUMN pr_checked_at INTEGER NOT NULL DEFAULT 0");
            ensureColumn(connection, "evolution_jobs", "primary_repair_rounds",
                    "ALTER TABLE evolution_jobs ADD COLUMN primary_repair_rounds INTEGER NOT NULL DEFAULT 0");
            ensureColumn(connection, "evolution_jobs", "diagnostic_repair_rounds",
                    "ALTER TABLE evolution_jobs ADD COLUMN diagnostic_repair_rounds INTEGER NOT NULL DEFAULT 0");
            ensureColumn(connection, "evolution_jobs", "last_failure_code",
                    "ALTER TABLE evolution_jobs ADD COLUMN last_failure_code TEXT NOT NULL DEFAULT ''");
            ensureColumn(connection, "evolution_jobs", "last_failure_category",
                    "ALTER TABLE evolution_jobs ADD COLUMN last_failure_category TEXT NOT NULL DEFAULT ''");
            ensureColumn(connection, "issue_codecheck_feedback", "head_sha",
                    "ALTER TABLE issue_codecheck_feedback ADD COLUMN head_sha TEXT NOT NULL DEFAULT ''");
            statement.executeUpdate("UPDATE evolution_jobs SET state='RETRY_SCHEDULED' "
                    + "WHERE state='FAILED_RETRYABLE'");
            statement.executeUpdate("UPDATE evolution_jobs SET state='FAILED_AUTOMATION' "
                    + "WHERE state='FAILED_FINAL'");
            statement.executeUpdate("INSERT OR IGNORE INTO issue_admissions"
                    + "(repo,issue_iid,first_delivery_id,admitted_at) "
                    + "SELECT repo,issue_iid,trigger_delivery_id,created_at FROM evolution_jobs");
            statement.executeUpdate("PRAGMA user_version=8");
        } catch (SQLException ex) {
            throw failure("initialize SQLite schema", ex);
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try {
            try (Statement statement = connection.createStatement()) {
                executePragma(statement, "PRAGMA foreign_keys=ON");
                executePragma(statement, "PRAGMA busy_timeout=5000");
            }
            return connection;
        } catch (SQLException ex) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                ex.addSuppressed(closeFailure);
            }
            throw ex;
        }
    }

    private static int insertDelivery(Connection connection, String id, String eventType, String hash)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO webhook_deliveries(delivery_id,event_type,payload_sha256,received_at) "
                + "VALUES(?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, value(eventType));
            statement.setString(3, value(hash));
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate();
        }
    }

    private static int insertIssueAdmission(Connection connection, IssueJobRequest request) throws SQLException {
        String sql = "INSERT OR IGNORE INTO issue_admissions"
                + "(repo,issue_iid,first_delivery_id,admitted_at) VALUES(?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.repository());
            statement.setLong(2, request.issueIid());
            statement.setString(3, request.deliveryId());
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate();
        }
    }

    private static EvolutionJob insertJob(Connection connection, IssueJobRequest request) throws SQLException {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO evolution_jobs(id,repo,issue_iid,issue_title,issue_url,state,"
                + "trigger_delivery_id,branch,created_at,updated_at) VALUES(?,?,?,?,?,'RECEIVED',?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, request.repository());
            statement.setLong(3, request.issueIid());
            statement.setString(4, value(request.title()));
            statement.setString(5, value(request.issueUrl()));
            statement.setString(6, request.deliveryId());
            statement.setString(7, request.branch());
            statement.setLong(8, now);
            statement.setLong(9, now);
            requireInserted(statement.executeUpdate(), "evolution job", id);
        }
        EvolutionJob job = requireById(connection, id);
        appendEvent(connection, id, EvolutionJobState.RECEIVED, EvolutionJobState.RECEIVED, "trigger accepted");
        return job;
    }

    private static Optional<EvolutionJob> findAnyByIssue(Connection connection, String repo, long iid)
            throws SQLException {
        Optional<EvolutionJob> active = findByIssue(connection, repo, iid, true);
        return active.isPresent() ? active : findByIssue(connection, repo, iid, false);
    }

    private static Optional<EvolutionJob> findByIssue(Connection connection, String repo, long iid, boolean active)
            throws SQLException {
        String sql = active ? FIND_ACTIVE_ISSUE_SQL : FIND_LATEST_ISSUE_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, repo);
            statement.setLong(2, iid);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readJob(result)) : Optional.empty();
            }
        }
    }

    private static EvolutionJob requireById(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Evolution job not found: " + id);
                }
                return readJob(result);
            }
        }
    }

    private static void lockJobForWrite(Connection connection, String jobId) throws SQLException {
        String sql = "UPDATE evolution_jobs SET updated_at=updated_at WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            requireUpdated(statement.executeUpdate(), jobId);
        }
    }

    private static EvolutionJob readJob(ResultSet result) throws SQLException {
        long prNumber = result.getLong("pr_number");
        boolean isPrNumberNull = result.wasNull();
        return EvolutionJob.builder()
                .id(result.getString("id"))
                .repository(result.getString("repo"))
                .issueIid(result.getLong("issue_iid"))
                .issueTitle(result.getString("issue_title"))
                .issueUrl(result.getString("issue_url"))
                .state(EvolutionJobState.valueOf(result.getString("state")))
                .triggerDeliveryId(result.getString("trigger_delivery_id"))
                .branch(result.getString("branch"))
                .headSha(result.getString("head_sha"))
                .pullRequestNumber(isPrNumberNull ? null : prNumber)
                .pullRequestUrl(result.getString("pr_url"))
                .draft(result.getInt("draft") == 1)
                .attemptCount(result.getInt("attempt_count"))
                .primaryRepairRounds(result.getInt("primary_repair_rounds"))
                .diagnosticRepairRounds(result.getInt("diagnostic_repair_rounds"))
                .lastFailureCode(result.getString("last_failure_code"))
                .lastFailureCategory(result.getString("last_failure_category"))
                .nextAttemptAt(result.getLong("next_attempt_at"))
                .leaseOwner(result.getString("lease_owner"))
                .leaseUntil(result.getLong("lease_until"))
                .version(result.getLong("version"))
                .lastError(result.getString("last_error"))
                .createdAt(result.getLong("created_at"))
                .updatedAt(result.getLong("updated_at"))
                .build();
    }

    private static void appendEvent(Connection connection, String jobId, EvolutionJobState from,
                                    EvolutionJobState to, String detail) throws SQLException {
        String sql = "INSERT INTO evolution_job_events(job_id,from_state,to_state,detail,created_at) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setString(2, from.name());
            statement.setString(3, to.name());
            statement.setString(4, safeDetail(detail));
            statement.setLong(5, System.currentTimeMillis());
            requireInserted(statement.executeUpdate(), "evolution job event", jobId);
        }
    }

    private static void executePragma(Statement statement, String sql) throws SQLException {
        boolean hasResult = statement.execute(sql);
        if (hasResult) {
            try (ResultSet result = statement.getResultSet()) {
                while (result.next()) {
                    LOGGER.debug("SQLite pragma {} returned a result", sql);
                }
            }
        }
    }

    private static void ensureColumn(Connection connection, String table, String column, String alterSql)
            throws SQLException {
        boolean hasColumn = false;
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equals(result.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
        }
        if (!hasColumn) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(alterSql);
            }
        }
    }

    private static void requireUpdated(int count, String jobId) {
        if (count != 1) {
            throw new IllegalStateException("Evolution job version conflict: " + jobId);
        }
    }

    private static void requireInserted(int count, String entity, String id) {
        if (count != 1) {
            throw new IllegalStateException("Unable to insert " + entity + ": " + id);
        }
    }

    private static void requireTransition(EvolutionJobState source, EvolutionJobState destination) {
        if (!source.canTransitionTo(destination)) {
            throw new IllegalStateException(
                    "Invalid evolution job transition: " + source + " -> " + destination);
        }
    }

    private static void rollback(Connection connection, Exception originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private static long retryDelay(int attemptCount) {
        return switch (attemptCount) {
            case 0, 1 -> Duration.ofSeconds(30).toMillis();
            case 2 -> Duration.ofMinutes(2).toMillis();
            case 3 -> Duration.ofMinutes(10).toMillis();
            case 4 -> Duration.ofMinutes(30).toMillis();
            default -> Duration.ofHours(2).toMillis();
        };
    }

    private static long leaseMillis(Duration leaseDuration) {
        Duration required = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (required.isZero() || required.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        try {
            return Math.max(1L, required.toMillis());
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("leaseDuration is too large", ex);
        }
    }

    private static long leaseDeadline(long now, Duration leaseDuration) {
        try {
            return Math.addExact(now, leaseMillis(leaseDuration));
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("leaseDuration is too large", ex);
        }
    }

    private static String requireText(String value, String name) {
        String required = Objects.requireNonNull(value, name + " must not be null");
        if (required.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return required;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static String safeDetail(String value) {
        String detail = value(value);
        return detail.length() <= MAX_DETAIL_LENGTH ? detail : detail.substring(0, MAX_DETAIL_LENGTH);
    }

    private static IllegalStateException failure(String action, SQLException ex) {
        return new IllegalStateException("Unable to " + action + ": " + ex.getMessage(), ex);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
