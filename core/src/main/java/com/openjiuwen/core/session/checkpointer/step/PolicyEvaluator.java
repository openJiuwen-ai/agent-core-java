/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

/**
 * Evaluates whether a step boundary should persist the state snapshot under
 * {@link StepCheckpointPolicy#STEP_AUTO}.
 *
 * <p>Decision rules, mirroring issue #195 section 3.7:</p>
 * <ul>
 *   <li>{@code STEP_OFF} never persists steps; {@code STEP_ALWAYS} always does;</li>
 *   <li>structural signals C1-C4 (workflow declared, HITL capability, tool
 *       count, LONG_RUNNING task type) are evaluated on every boundary - once
 *       any hits, all later boundaries persist as well, so callers do not need
 *       to cache the up-front verdict;</li>
 *   <li>dynamic signals D1/D2 (completed steps, single-step duration) provide
 *       late activation for tasks whose shape was unknown before execution;</li>
 *   <li>the override value (invoke level) wins over the agent default.</li>
 * </ul>
 *
 * <p>The evaluator is stateless; dynamic counters live in the caller's
 * {@link StepSignalContext}, so one evaluator instance can be shared.</p>
 *
 * @since 0.1.17
 */
public final class PolicyEvaluator {
    private final StepAutoThresholds thresholds;

    public PolicyEvaluator() {
        this(new StepAutoThresholds());
    }

    /**
     * Create an evaluator with custom AUTO thresholds.
     *
     * @param thresholds thresholds applied to AUTO signal checks; null falls back to defaults
     */
    public PolicyEvaluator(StepAutoThresholds thresholds) {
        this.thresholds = thresholds == null ? new StepAutoThresholds() : thresholds;
    }

    /**
     * Resolve the policy for the current invocation: the invoke-level override
     * wins, otherwise the agent-level default applies.
     *
     * @param agentDefault policy configured on the agent; null falls back to {@code STEP_AUTO}
     * @param invokeOverride per-invocation override; null means "not overridden"
     * @return the effective policy, never null
     */
    public StepCheckpointPolicy resolve(StepCheckpointPolicy agentDefault, StepCheckpointPolicy invokeOverride) {
        if (invokeOverride != null) {
            return invokeOverride;
        }
        return agentDefault == null ? StepCheckpointPolicy.STEP_AUTO : agentDefault;
    }

    /**
     * Whether the given step boundary should persist the state snapshot.
     *
     * @param agentDefault policy configured on the agent; null falls back to {@code STEP_AUTO}
     * @param invokeOverride per-invocation override; null means "not overridden"
     * @param signal execution-time facts for AUTO evaluation; null yields an empty context
     * @return true when the boundary must flush the snapshot through the checkpointer
     */
    public boolean shouldPersist(StepCheckpointPolicy agentDefault, StepCheckpointPolicy invokeOverride,
                                 StepSignalContext signal) {
        StepCheckpointPolicy policy = resolve(agentDefault, invokeOverride);
        if (policy == StepCheckpointPolicy.STEP_ALWAYS) {
            return true;
        }
        if (policy == StepCheckpointPolicy.STEP_OFF) {
            return false;
        }
        return matchesAutoSignals(signal == null ? new StepSignalContext() : signal);
    }

    private boolean matchesAutoSignals(StepSignalContext signal) {
        return hasLongRunningTaskType(signal)
                || signal.isHasWorkflow()
                || signal.isHasInteractiveCapability()
                || signal.getToolCount() >= thresholds.getMinToolCount()
                || signal.getCompletedSteps() >= thresholds.getMinCompletedSteps()
                || signal.getLastStepDurationMillis() >= thresholds.getStepDurationMillis();
    }

    private static boolean hasLongRunningTaskType(StepSignalContext signal) {
        return "LONG_RUNNING".equalsIgnoreCase(signal.getTaskType());
    }
}
