/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Agent lifecycle callback event types.
 *
 * <p>Mirrors Python's {@code AgentCallbackEvent} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
public enum AgentCallbackEvent {
    BEFORE_INVOKE("before_invoke"),
    AFTER_INVOKE("after_invoke"),
    BEFORE_TASK_ITERATION("before_task_iteration"),
    AFTER_TASK_ITERATION("after_task_iteration"),
    BEFORE_MODEL_CALL("before_model_call"),
    AFTER_MODEL_CALL("after_model_call"),
    ON_MODEL_EXCEPTION("on_model_exception"),
    BEFORE_TOOL_CALL("before_tool_call"),
    AFTER_TOOL_CALL("after_tool_call"),
    ON_TOOL_EXCEPTION("on_tool_exception");

    private final String value;

    AgentCallbackEvent(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AgentCallbackEvent fromValue(String value) {
        for (AgentCallbackEvent event : values()) {
            if (event.value.equals(value)) {
                return event;
            }
        }
        return null;
    }
}
