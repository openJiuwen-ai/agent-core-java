/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

/**
 * Stable failure categories returned by an Issue execution.
 *
 * @since 0.1.12
 */
public enum IssueExecutionErrorCode {
    NONE,
    EXECUTION_FAILED,
    WORKTREE_INFRASTRUCTURE_FAILED,
    AGENT_INFRASTRUCTURE_FAILED,
    CI_INFRASTRUCTURE_FAILED,
    VERIFICATION_FAILED,
    COMMIT_INFRASTRUCTURE_FAILED,
    COMMIT_VALIDATION_FAILED,
    PUBLISH_FAILED,
    PUBLISH_NOTIFICATION_FAILED,
    GITCODE_API_FAILED,
    WORKER_INFRASTRUCTURE_FAILED,
    OUTSIDE_SPARSE_CHECKOUT_SCOPE,
    TARGET_PATH_NOT_FOUND,
    EARLY_E2E_TEST_TARGET_REQUIRED;

    /**
     * Prefix a safe detail with this stable category exactly once.
     *
     * @param detail safe failure detail
     * @return typed failure detail
     */
    public String format(String detail) {
        String safeDetail = detail == null ? "" : detail.strip();
        if (this == NONE) {
            return safeDetail;
        }
        String prefix = name() + ":";
        if (safeDetail.startsWith(prefix)) {
            return safeDetail;
        }
        return safeDetail.isBlank() ? prefix + " operation failed" : prefix + " " + safeDetail;
    }
}
