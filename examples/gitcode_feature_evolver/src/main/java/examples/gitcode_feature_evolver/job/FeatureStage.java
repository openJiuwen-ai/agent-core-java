/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.util.EnumSet;
import java.util.Set;

/**
 * Persisted controller states for one feature delivery.
 *
 * @since 0.1.12
 */
public enum FeatureStage {
    ADMITTED,
    SPECIFY,
    REVIEW_R1,
    CREATE_DRAFT_PR,
    DESIGN,
    REVIEW_R2,
    IMPLEMENT_RED,
    IMPLEMENT_GREEN,
    IMPLEMENT_REFACTOR,
    IMPLEMENT_REWORK,
    PUBLISH_TASK,
    REVIEW_R3,
    SHIP,
    READY_FOR_REVIEW,
    SYSTEM_TEST,
    REVIEW_SYSTEM_TEST,
    PUBLISH_SYSTEM_TEST,
    SYSTEM_TEST_READY_FOR_REVIEW,
    PAUSED,
    RETRY_SCHEDULED,
    DEPENDENCY_PREFETCH,
    BLOCKED_EXTERNAL,
    CANCEL_REQUESTED,
    CANCELLED,
    MERGED,
    CLOSED,
    FAILED_AUTOMATION,
    FAILED_CONFIGURATION,
    FAILED_POLICY,
    FAILED_INTERNAL;

    private static final Set<FeatureStage> RUNNABLE = EnumSet.of(
            ADMITTED, SPECIFY, REVIEW_R1, CREATE_DRAFT_PR, DESIGN, REVIEW_R2,
            IMPLEMENT_RED, IMPLEMENT_GREEN, IMPLEMENT_REFACTOR, IMPLEMENT_REWORK, PUBLISH_TASK,
            REVIEW_R3, SHIP, SYSTEM_TEST, REVIEW_SYSTEM_TEST, PUBLISH_SYSTEM_TEST,
            CANCEL_REQUESTED, RETRY_SCHEDULED, DEPENDENCY_PREFETCH);
    private static final Set<FeatureStage> TERMINAL = EnumSet.of(
            CANCELLED, MERGED, CLOSED, BLOCKED_EXTERNAL, FAILED_AUTOMATION,
            FAILED_CONFIGURATION, FAILED_POLICY, FAILED_INTERNAL);

    /**
     * Report whether a worker may lease this state.
     *
     * @return {@code true} for controller-executable states
     */
    public boolean isRunnable() {
        return RUNNABLE.contains(this);
    }

    /**
     * Report whether no automatic or human transition remains.
     *
     * @return {@code true} for terminal states
     */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * Report whether this state waits for GitCode review and merge.
     *
     * @return {@code true} for either normal PR review boundary
     */
    public boolean isApprovalWait() {
        return this == READY_FOR_REVIEW || this == SYSTEM_TEST_READY_FOR_REVIEW;
    }
}
