/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

/** Controller-authoritative Issue execution failure categories. */
public enum IssueFailureCategory {
    AGENT_CORRECTABLE,
    TRANSIENT_MODEL,
    TRANSIENT_GITCODE,
    TRANSIENT_INFRASTRUCTURE,
    DEPENDENCY_MISSING,
    POLICY_VIOLATION,
    CONFIGURATION,
    PRODUCT_DECISION,
    ENVIRONMENT_BLOCKER,
    INTERNAL
}
