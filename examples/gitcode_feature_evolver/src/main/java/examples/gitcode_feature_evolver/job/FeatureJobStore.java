/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable feature workflow, admission, command, and polling boundary.
 *
 * @since 0.1.12
 */
public interface FeatureJobStore extends AutoCloseable {
    /** Admit one Issue at most once across every trigger channel. */
    AdmissionResult admit(FeatureJobRequest request);

    /** Find a job by stable ID. */
    Optional<FeatureJob> findById(String jobId);

    /** Find the lifetime job for a repository-scoped Issue. */
    Optional<FeatureJob> findByIssue(String repository, long issueIid);

    /** Find a job by its canonical pull-request binding. */
    Optional<FeatureJob> findByPullRequest(String repository, long pullRequestNumber);

    /** Find a job by its canonical system-test pull-request binding. */
    Optional<FeatureJob> findBySystemTestPullRequest(long pullRequestNumber);

    /** Record and deduplicate a non-admission Webhook delivery. */
    boolean acceptDelivery(FeatureJobRequest.Delivery delivery, Instant observedAt, String reason);

    /** Lease one runnable job using an atomic compare-and-set update. */
    Optional<FeatureJob> leaseNext(String workerId, Instant now, Duration duration);

    /** Extend a currently owned lease. */
    boolean heartbeat(String jobId, String workerId, Instant now, Duration duration);

    /** Clear stale leases without changing their workflow state. */
    void recoverExpiredLeases(Instant now);

    /**
     * Release every lease owned by one process-unique worker.
     *
     * <p>The optimistic-lock version is incremented so a cancelled stage cannot
     * persist a late result after shutdown.</p>
     *
     * @param workerId process-unique lease owner
     * @param now release observation time
     * @return number of released leases
     */
    int releaseLeases(String workerId, Instant now);

    /** Apply one optimistic stage update and release its worker lease. */
    FeatureJob transition(String jobId, long version, FeatureJobMutation mutation);

    /** Bind or update the one canonical pull request. */
    FeatureJob recordPullRequest(String jobId, long version, FeatureJob.PullRequest pullRequest);

    /** Bind or update the one canonical post-merge system-test pull request. */
    FeatureJob recordSystemTestPullRequest(
            String jobId, long version, FeatureJob.PullRequest pullRequest);

    /** Apply an authenticated, deduplicated human command. */
    CommandResult applyCommand(FeatureCommand command);

    /** Load the frozen updated-at pagination checkpoint. */
    Optional<FeatureScanCheckpoint> loadCheckpoint(String repository, String label);

    /** Save a checkpoint only after the corresponding page succeeded. */
    void saveCheckpoint(FeatureScanCheckpoint checkpoint);

    /** Remove a completed scan checkpoint. */
    void clearCheckpoint(String repository, String label);

    /** List PR-bound nonterminal jobs using oldest-check-first ordering. */
    List<FeatureJob> listPullRequestsForReconciliation(int limit);

    /** List system-test-PR-bound nonterminal jobs using oldest-check-first ordering. */
    List<FeatureJob> listSystemTestPullRequestsForReconciliation(int limit);

    /** List nonterminal jobs whose Issue comments may contain controller commands. */
    List<FeatureJob> listJobsForCommandPolling(int limit);

    /** List recent Jobs within this store's optional repository scope. */
    List<FeatureJob> listRecentJobs(int limit);

    /** List recent audit events within this store's optional repository scope. */
    List<FeatureAuditEvent> listRecentAuditEvents(int limit);

    /** Persist a classified failure and update durable recovery counters. */
    FeatureJob recordFailure(String jobId, long version, FeatureFailure failure,
                             FeatureFailureEvent.RepairAttempt attempt, long nextRetryAt);

    /** Persist a successful recovery action without manufacturing a failure event. */
    FeatureJob recordRecoveryProgress(String jobId, long version,
                                      FeatureFailureEvent.RepairAttempt attempt,
                                      String summary);

    /** Load bounded failure history used to rebuild Controller repair context. */
    List<FeatureFailureEvent> listFailureEvents(String jobId, int limit);

    /** Find a deterministic Gate receipt for the exact immutable input fingerprint. */
    Optional<ApprovedGateReceipt> findGateReceipt(String jobId, FeatureStage stage,
                                                  String profile, String fingerprint);

    /** Persist or reconcile one deterministic Gate receipt. */
    ApprovedGateReceipt recordGateReceipt(ApprovedGateReceipt receipt);

    /** Remove a legacy receipt whose result is not safe for deterministic reuse. */
    void discardGateReceipt(ApprovedGateReceipt receipt);

    /** Find the most recently completed Gate receipt for one Job. */
    Optional<ApprovedGateReceipt> findLatestGateReceipt(String jobId);

    /** Find the most recently completed Gate receipt for one Job stage. */
    Optional<ApprovedGateReceipt> findLatestGateReceipt(String jobId, FeatureStage stage);

    /** Record reuse of an existing deterministic Gate receipt. */
    void recordGateCacheHit(ApprovedGateReceipt receipt);

    /** Record one successful nonterminal PR check. */
    void markPullRequestChecked(String jobId, long checkedAt);

    /** Record one successful nonterminal system-test PR check. */
    void markSystemTestPullRequestChecked(String jobId, long checkedAt);

    /** Close the store. Implementations may use short-lived connections. */
    @Override
    void close();
}
