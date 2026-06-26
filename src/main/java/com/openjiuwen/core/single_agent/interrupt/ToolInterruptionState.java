/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool interruption state for resume support.
 *
 * <p>Mirrors Python's {@code ToolInterruptionState} in
 * {@code openjiuwen/core/single_agent/interrupt/state.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolInterruptionState extends BaseInterruptionState {
    @JsonProperty("interrupted_tools")
    private Map<String, ToolInterruptEntry> interruptedTools = new LinkedHashMap<>();

    @JsonProperty("auto_confirm_mapping")
    private Map<String, String> autoConfirmMapping = new LinkedHashMap<>();

    public Map<String, ToolInterruptEntry> getInterruptedTools() {
        return interruptedTools;
    }

    public void setInterruptedTools(Map<String, ToolInterruptEntry> interruptedTools) {
        this.interruptedTools = interruptedTools == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(interruptedTools);
    }

    public Map<String, String> getAutoConfirmMapping() {
        return autoConfirmMapping;
    }

    public void setAutoConfirmMapping(Map<String, String> autoConfirmMapping) {
        this.autoConfirmMapping = autoConfirmMapping == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(autoConfirmMapping);
    }
}
