/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime context passed to each StopConditionEvaluator.
 *
 * <p>Decoupled from AgentCallbackContext so evaluators do not
 * depend on the agent callback system.
 *
 * <p>Mirrors Python's {@code StopEvaluationContext} in
 * {@code openjiuwen.harness.schema.stop_condition}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopEvaluationContext {

    /** Number of completed outer-loop rounds. */
    @Builder.Default
    private int iteration = 0;

    /** Cumulative token usage across all rounds. */
    @Builder.Default
    private int tokenUsage = 0;

    /** Wall-clock seconds since loop start. */
    @Builder.Default
    private double elapsedSeconds = 0.0;

    /** Result dict from the most recent round. */
    private Map<String, Object> lastResult;

    /** Arbitrary extra data for custom evaluators. */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
}