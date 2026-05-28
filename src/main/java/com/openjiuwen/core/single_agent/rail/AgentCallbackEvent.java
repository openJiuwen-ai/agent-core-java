/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

/**
 * Agent callback event types for agent lifecycle.
 *
 * <p>Mirrors Python's {@code AgentCallbackEvent} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
public enum AgentCallbackEvent {

    /** Before agent.invoke() starts. */
    BEFORE_INVOKE("before_invoke"),

    /** After agent.invoke() completes. */
    AFTER_INVOKE("after_invoke"),

    /** Before LLM is called. */
    BEFORE_MODEL_CALL("before_model_call"),

    /** After LLM response is received. */
    AFTER_MODEL_CALL("after_model_call"),

    /** When LLM call raises. */
    ON_MODEL_EXCEPTION("on_model_exception"),

    /** Before a tool is executed. */
    BEFORE_TOOL_CALL("before_tool_call"),

    /** After tool execution completes. */
    AFTER_TOOL_CALL("after_tool_call"),

    /** When tool execution raises. */
    ON_TOOL_EXCEPTION("on_tool_exception");

    private final String value;

    AgentCallbackEvent(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}