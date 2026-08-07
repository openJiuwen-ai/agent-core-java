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
    WAIT_R1_APPROVAL,
    CREATE_DRAFT_PR,
    DESIGN,
    REVIEW_R2,
    WAIT_R2_APPROVAL,
    IMPLEMENT_RED,
    IMPLEMENT_GREEN,
    IMPLEMENT_REFACTOR,
    PUBLISH_TASK,
    REVIEW_R3,
    WAIT_R3_APPROVAL,
    SHIP,
    READY_FOR_REVIEW,
    PAUSED,
    WAITING_HUMAN,
    WAITING_DEPENDENCY_PREFETCH,
    CANCEL_REQUESTED,
    CANCELLED,
    MERGED,
    CLOSED,
    FAILED_RETRYABLE,
    FAILED_FINAL;

    private static final Set<FeatureStage> RUNNABLE = EnumSet.of(
            ADMITTED, SPECIFY, REVIEW_R1, CREATE_DRAFT_PR, DESIGN, REVIEW_R2,
            IMPLEMENT_RED, IMPLEMENT_GREEN, IMPLEMENT_REFACTOR, PUBLISH_TASK,
            REVIEW_R3, SHIP,
            CANCEL_REQUESTED, FAILED_RETRYABLE);
    private static final Set<FeatureStage> TERMINAL = EnumSet.of(
            CANCELLED, MERGED, CLOSED, FAILED_FINAL);

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
     * Report whether this state is one of the authenticated human gates.
     *
     * @return {@code true} for R1, R2, or R3 approval waits
     */
    public boolean isApprovalWait() {
        return this == WAIT_R1_APPROVAL || this == WAIT_R2_APPROVAL || this == WAIT_R3_APPROVAL;
    }
}
