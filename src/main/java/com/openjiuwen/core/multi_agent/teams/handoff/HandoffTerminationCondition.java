/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

/**
 * Dynamic termination predicate called with the handoff orchestrator instance.
 *
 * <p>Mirrors Python's {@code termination_condition} callable in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 */
@FunctionalInterface
public interface HandoffTerminationCondition {

    /**
     * Evaluates whether handoff orchestration should stop early.
     *
     * @param handoffOrchestrator runtime handoff orchestrator object
     * @return {@code true} to terminate early
     */
    boolean shouldTerminate(Object handoffOrchestrator);
}
