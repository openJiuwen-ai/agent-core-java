/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import examples.gitcode_issue_evolver.curation.CodingStandardCurationTask;
import examples.gitcode_issue_evolver.curation.CodingStandardLesson;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for webhook deliveries and evolution jobs.
 *
 * @since 0.1.12
 */
public interface EvolutionJobStore extends AutoCloseable {
    /**
     * Persist a webhook delivery when its identifier is new.
     *
     * @param deliveryId provider delivery identifier
     * @param eventType provider event type
     * @param payloadSha256 payload digest
     * @return {@code true} when the delivery was inserted
     */
    boolean acceptDelivery(String deliveryId, String eventType, String payloadSha256);

    /**
     * Atomically accept a delivery and create at most one active Issue job.
     *
     * @param request normalized Issue job request
     * @return enqueue result
     */
    EnqueueResult enqueueIssue(IssueJobRequest request);

    /**
     * Return an active job or create one when absent.
     *
     * @param request normalized Issue job request
     * @return existing or created job
     */
    Optional<EvolutionJob> createJobIfAbsent(IssueJobRequest request);

    /**
     * Find the active or most recent job for an Issue.
     *
     * @param repository fixed repository path
     * @param issueIid Issue IID
     * @return matching job
     */
    Optional<EvolutionJob> findByIssue(String repository, long issueIid);

    /**
     * Find one durable job by its identifier.
     *
     * @param jobId durable job identifier
     * @return matching job
     */
    Optional<EvolutionJob> findById(String jobId);

    /**
     * Find the job bound to a PR.
     *
     * @param repository fixed repository path
     * @param pullRequestNumber PR number
     * @return matching job
     */
    Optional<EvolutionJob> findByPullRequest(String repository, long pullRequestNumber);

    /**
     * Load an unfinished Issue polling window.
     *
     * @param repository target repository
     * @param label exact trigger label
     * @return durable continuation when one exists
     */
    Optional<IssueScanCheckpoint> loadIssueScanCheckpoint(String repository, String label);

    /**
     * Save the next page for an unfinished Issue polling window.
     *
     * @param checkpoint durable continuation
     */
    void saveIssueScanCheckpoint(IssueScanCheckpoint checkpoint);

    /**
     * Clear a completed Issue polling window.
     *
     * @param repository target repository
     * @param label exact trigger label
     */
    void clearIssueScanCheckpoint(String repository, String label);

    /**
     * Return review-waiting jobs least recently reconciled first.
     *
     * @param limit maximum jobs returned
     * @return immutable job snapshots
     */
    List<EvolutionJob> listPullRequestsForReconciliation(int limit);

    /**
     * Record a successful open-PR reconciliation without changing job state.
     *
     * @param jobId durable job identifier
     * @param checkedAt reconciliation epoch milliseconds
     */
    void markPullRequestChecked(String jobId, long checkedAt);

    /**
     * Atomically deduplicate trusted CodeCheck feedback and schedule same-PR repair.
     *
     * @param request bounded feedback admission
     * @return updated Job, or empty when the same feedback was already admitted
     */
    Optional<EvolutionJob> scheduleCodeCheckRepair(CodeCheckRepairRequest request);

    /**
     * Find one successful CodeCheck feedback set awaiting independent curation.
     *
     * @return eligible task only after its Issue job reached MERGED
     */
    Optional<CodingStandardCurationTask> nextCodingStandardCurationTask();

    /**
     * Atomically persist validated lessons and complete their curation task.
     *
     * @param task source task
     * @param lessons validated lessons, possibly empty
     */
    void completeCodingStandardCuration(CodingStandardCurationTask task,
                                        List<CodingStandardLesson> lessons);

    /**
     * Record a bounded curation failure and schedule or exhaust its retry.
     *
     * @param task source task
     * @param error safe diagnostic
     * @param maximumAttempts maximum total attempts
     */
    void failCodingStandardCuration(CodingStandardCurationTask task, String error,
                                    int maximumAttempts);

    /**
     * Return newest trusted prevention lessons for future Issue worker sessions.
     *
     * @param limit maximum lessons
     * @return immutable lessons
     */
    List<CodingStandardLesson> listCodingStandardLessons(int limit);

    /** Persist one bounded Controller-classified failure event. */
    void recordFailureEvent(String jobId, String stage, String code,
                            IssueFailureCategory category, String summary, String diagnostic);

    /** Upsert one bounded Approved Gate receipt by Job and input fingerprint. */
    void recordGateReceipt(String jobId, String fingerprint, String status,
                           String profile, String code, String category,
                           boolean cached, int exitCode, String outputTail, long completedAt);

    /** Find a deterministic Gate receipt for one exact input fingerprint. */
    Optional<IssueGateReceipt> findGateReceipt(String jobId, String fingerprint);

    /** Persist the latest in-stage repair counters and failure identity. */
    void recordRepairProgress(String jobId, int primaryRounds, int diagnosticRounds,
                              String failureCode, String failureCategory);

    /** Return newest bounded failure context first for repair reconstruction. */
    List<String> recentFailureContext(String jobId, int limit);

    /**
     * Atomically lease the next eligible job.
     *
     * @param workerId lease owner
     * @param leaseDuration lease duration
     * @return leased job
     */
    Optional<EvolutionJob> leaseNext(String workerId, Duration leaseDuration);

    /**
     * Extend a lease owned by the worker.
     *
     * @param jobId job identifier
     * @param workerId lease owner
     * @param leaseDuration new lease duration
     * @return {@code true} when the lease was extended
     */
    boolean heartbeat(String jobId, String workerId, Duration leaseDuration);

    /**
     * Apply one optimistic-lock state transition.
     *
     * @param jobId job identifier
     * @param expectedVersion expected row version
     * @param state destination state
     * @param error safe diagnostic text
     * @return updated job
     */
    EvolutionJob transition(String jobId, long expectedVersion, EvolutionJobState state, String error);

    /**
     * Idempotently request cancellation without releasing the Issue uniqueness slot.
     *
     * @param jobId durable job identifier
     * @param reason safe auditable reason
     * @return current cancellation snapshot
     */
    EvolutionJob requestCancellation(String jobId, String reason);

    /**
     * Bind one created or reconciled PR using optimistic locking.
     *
     * @param jobId job identifier
     * @param expectedVersion expected row version
     * @param number PR number
     * @param url PR web URL
     * @param headSha PR head commit
     * @param draft whether the PR is Draft
     * @return updated job
     */
    EvolutionJob recordPullRequest(String jobId, long expectedVersion, long number,
                                   String url, String headSha, boolean draft);

    /** Recover jobs whose worker lease expired. */
    void recoverExpiredLeases();

    @Override
    default void close() {
    }
}
