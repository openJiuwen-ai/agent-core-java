/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import java.util.Objects;

/**
 * Tunable thresholds for {@link StepCheckpointPolicy#STEP_AUTO} signal evaluation.
 *
 * <p>Defaults follow issue #195 section 3.7.2: tools >= 8, completed steps >= 3,
 * single-step duration >= 5s. Values are overridable per agent config; the
 * first tuning pass happens after load testing.</p>
 *
 * @since 0.1.17
 */
public class StepAutoThresholds {
    /** Default tool-count threshold for structural signal C3. */
    public static final int DEFAULT_TOOL_COUNT = 8;

    /** Default completed-step threshold for dynamic signal D1 (late activation). */
    public static final int DEFAULT_COMPLETED_STEPS = 3;

    /** Default single-step duration threshold in millis for dynamic signal D2. */
    public static final long DEFAULT_STEP_DURATION_MILLIS = 5_000L;

    private int minToolCount = DEFAULT_TOOL_COUNT;
    private int minCompletedSteps = DEFAULT_COMPLETED_STEPS;
    private long stepDurationMillis = DEFAULT_STEP_DURATION_MILLIS;

    public StepAutoThresholds() {
    }

    /**
     * Create thresholds with explicit values.
     *
     * @param minToolCount structural signal C3: mounted tool count at or above this enables steps
     * @param minCompletedSteps dynamic signal D1: completed steps at or above this enables steps
     * @param stepDurationMillis dynamic signal D2: single step at or above this duration enables steps
     */
    public StepAutoThresholds(int minToolCount, int minCompletedSteps, long stepDurationMillis) {
        this.minToolCount = minToolCount;
        this.minCompletedSteps = minCompletedSteps;
        this.stepDurationMillis = stepDurationMillis;
    }

    public int getMinToolCount() {
        return minToolCount;
    }

    public void setMinToolCount(int minToolCount) {
        this.minToolCount = minToolCount;
    }

    public int getMinCompletedSteps() {
        return minCompletedSteps;
    }

    public void setMinCompletedSteps(int minCompletedSteps) {
        this.minCompletedSteps = minCompletedSteps;
    }

    public long getStepDurationMillis() {
        return stepDurationMillis;
    }

    public void setStepDurationMillis(long stepDurationMillis) {
        this.stepDurationMillis = stepDurationMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StepAutoThresholds thresholds)) {
            return false;
        }
        return minToolCount == thresholds.minToolCount
                && minCompletedSteps == thresholds.minCompletedSteps
                && stepDurationMillis == thresholds.stepDurationMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minToolCount, minCompletedSteps, stepDurationMillis);
    }

    @Override
    public String toString() {
        return "StepAutoThresholds{minToolCount=" + minToolCount
                + ", minCompletedSteps=" + minCompletedSteps
                + ", stepDurationMillis=" + stepDurationMillis + "}";
    }
}
