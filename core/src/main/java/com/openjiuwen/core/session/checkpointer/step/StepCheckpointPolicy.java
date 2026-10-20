/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

/**
 * Step-level checkpoint policy controlling when step boundaries flush the
 * state snapshot through the checkpointer.
 *
 * <p>The policy only governs step-level persistence. The existing round-level
 * checkpointer hooks ({@code preAgentExecute}/{@code postAgentExecute}) keep
 * working in every mode, so {@link #STEP_OFF} means "no extra per-step cost",
 * never "checkpointer disabled". HITL interrupt points bypass the policy and
 * always persist, because they are correctness rather than performance.</p>
 *
 * @since 0.1.17
 */
public enum StepCheckpointPolicy {
    /** No step-level persistence; round-level checkpoints remain active. */
    STEP_OFF,

    /** Persist the state snapshot at every step boundary. */
    STEP_ALWAYS,

    /**
     * Framework decides: structural signals (workflow, HITL capability, tool
     * count, task type) evaluated up front, dynamic signals (completed step
     * count, step duration) trigger late activation during execution.
     */
    STEP_AUTO;

    /**
     * Parse a policy from a config value.
     *
     * @param value raw config value, may be null
     * @return the parsed policy, or null when the value does not name a policy
     */
    public static StepCheckpointPolicy fromString(Object value) {
        if (value == null) {
            return null;
        }
        String name = String.valueOf(value);
        for (StepCheckpointPolicy policy : values()) {
            if (policy.name().equals(name)) {
                return policy;
            }
        }
        return null;
    }
}
