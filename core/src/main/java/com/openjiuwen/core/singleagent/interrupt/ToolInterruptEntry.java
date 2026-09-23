/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One interrupted outer tool call and its inner interrupt requests.
 *
 * <p>Mirrors Python's {@code ToolInterruptEntry} in
 * {@code openjiuwen/core/single_agent/interrupt/state.py}.</p>
 *
 * <p>Reachable from {@link ToolInterruptionState}, so it has to be {@link Serializable} for a
 * persisting checkpointer to write the interrupted turn.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolInterruptEntry implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("tool_call")
    private ToolCall toolCall;

    @JsonProperty("interrupt_requests")
    private Map<String, InterruptRequest> interruptRequests = new LinkedHashMap<>();

    @JsonProperty("is_sub_agent")
    private boolean subAgent;

    public ToolCall getToolCall() {
        return toolCall;
    }

    public void setToolCall(ToolCall toolCall) {
        this.toolCall = toolCall;
    }

    public Map<String, InterruptRequest> getInterruptRequests() {
        return interruptRequests;
    }

    public void setInterruptRequests(Map<String, InterruptRequest> interruptRequests) {
        this.interruptRequests = interruptRequests == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(interruptRequests);
    }

    public boolean isSubAgent() {
        return subAgent;
    }

    public void setSubAgent(boolean subAgent) {
        this.subAgent = subAgent;
    }
}
