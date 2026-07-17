/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import java.time.Duration;
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
