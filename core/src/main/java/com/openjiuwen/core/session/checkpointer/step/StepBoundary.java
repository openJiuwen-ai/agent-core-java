/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

/**
 * One completed step boundary observed by an agent, grouping the step
 * identity fields passed to {@link StepCheckpointTrigger#stepCompleted}.
 *
 * @param stepType step category, e.g. TOOL_CALL / LLM_RESPONSE / PHASE
 * @param stepName tool name or phase name
 * @param status completion status of the step
 * @param stepDurationMillis duration of the step in millis, for D2 and audit
 * @since 0.1.17
 */
public record StepBoundary(String stepType, String stepName, StepStatus status, long stepDurationMillis) {
}
