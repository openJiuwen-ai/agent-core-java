/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import lombok.Builder;

/**
 * Immutable snapshot of a durable Issue evolution job.
 *
 * @since 0.1.12
 */
@Builder
public record EvolutionJob(
        String id,
        String repository,
        long issueIid,
        String issueTitle,
        String issueUrl,
        EvolutionJobState state,
        String triggerDeliveryId,
        String branch,
        String headSha,
        Long pullRequestNumber,
        String pullRequestUrl,
        boolean draft,
        int attemptCount,
        long nextAttemptAt,
        String leaseOwner,
        long leaseUntil,
        long version,
        String lastError,
        long createdAt,
        long updatedAt) {
}
