/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

/**
 * Controller-authoritative failure categories for Feature Evolver execution.
 *
 * @since 0.1.12
 */
public enum FeatureFailureCategory {
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
