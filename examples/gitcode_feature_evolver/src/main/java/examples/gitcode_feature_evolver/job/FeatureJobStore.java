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

    /** Record and deduplicate a non-admission Webhook delivery. */
    boolean acceptDelivery(FeatureJobRequest.Delivery delivery, Instant observedAt, String reason);

    /** Lease one runnable job using an atomic compare-and-set update. */
    Optional<FeatureJob> leaseNext(String workerId, Instant now, Duration duration);

    /** Extend a currently owned lease. */
    boolean heartbeat(String jobId, String workerId, Instant now, Duration duration);

    /** Clear stale leases without changing their workflow state. */
    void recoverExpiredLeases(Instant now);

    /** Apply one optimistic stage update and release its worker lease. */
    FeatureJob transition(String jobId, long version, FeatureJobMutation mutation);

    /** Bind or update the one canonical pull request. */
    FeatureJob recordPullRequest(String jobId, long version, FeatureJob.PullRequest pullRequest);

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

    /** List nonterminal jobs whose Issue comments may contain controller commands. */
    List<FeatureJob> listJobsForCommandPolling(int limit);

    /** Record one successful nonterminal PR check. */
    void markPullRequestChecked(String jobId, long checkedAt);

    /** Close the store. Implementations may use short-lived connections. */
    @Override
    void close();
}
