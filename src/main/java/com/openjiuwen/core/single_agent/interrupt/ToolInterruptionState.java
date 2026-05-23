/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool interruption state for resume support.
 *
 * <p>Mirrors Python's {@code ToolInterruptionState} in
 * {@code openjiuwen.core.single_agent.interrupt.state}.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ToolInterruptionState extends BaseInterruptionState {

    /** Mapping of outer_id (tool/subagent) to ToolInterruptEntry. */
    private Map<String, ToolInterruptEntry> interruptedTools = new HashMap<>();

    /** Mapping of inner_id to auto-confirm key. */
    private Map<String, String> autoConfirmMapping = new HashMap<>();

    /**
     * Create a builder-compatible instance with all fields set.
     */
    public static ToolInterruptionState create(
            com.openjiuwen.core.foundation.llm.schema.AssistantMessage aiMessage,
            int iteration,
            String originalQuery,
            Map<String, ToolInterruptEntry> interruptedTools,
            Map<String, String> autoConfirmMapping) {
        ToolInterruptionState state = new ToolInterruptionState();
        state.setAiMessage(aiMessage);
        state.setIteration(iteration);
        state.setOriginalQuery(originalQuery);
        state.setInterruptedTools(interruptedTools);
        state.setAutoConfirmMapping(autoConfirmMapping);
        return state;
    }
}