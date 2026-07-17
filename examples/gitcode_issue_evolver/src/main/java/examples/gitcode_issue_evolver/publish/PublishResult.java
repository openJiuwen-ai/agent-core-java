/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.publish;

import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of push, PR creation or reconciliation, and Issue notification.
 *
 * @since 0.1.12
 */
public record PublishResult(boolean success, boolean retryable, boolean reusedPullRequest,
                            boolean notificationSucceeded, Optional<GitCodePullRequest> pullRequest,
                            String error) {
    public PublishResult(boolean success, boolean retryable, boolean reusedPullRequest,
                         boolean notificationSucceeded, Optional<GitCodePullRequest> pullRequest,
                         String error) {
        this.success = success;
        this.retryable = retryable;
        this.reusedPullRequest = reusedPullRequest;
        this.notificationSucceeded = notificationSucceeded;
        this.pullRequest = Objects.requireNonNull(pullRequest, "pullRequest must not be null");
        this.error = error == null ? "" : error;
    }

    /**
     * Create a failed publication result.
     *
     * @param error safe error text
     * @param retryable whether infrastructure retry is allowed
     * @return failed result
     */
    public static PublishResult failed(String error, boolean retryable) {
        return new PublishResult(false, retryable, false, false, Optional.empty(), error);
    }
}
