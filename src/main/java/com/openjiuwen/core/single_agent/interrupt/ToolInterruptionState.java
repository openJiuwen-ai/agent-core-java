/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInterruptionState extends BaseInterruptionState {

    /** Mapping of outer_id (tool/subagent) to ToolInterruptEntry. */
    @Builder.Default
    private Map<String, ToolInterruptEntry> interruptedTools = new HashMap<>();

    /** Mapping of inner_id to auto-confirm key. */
    @Builder.Default
    private Map<String, String> autoConfirmMapping = new HashMap<>();
}