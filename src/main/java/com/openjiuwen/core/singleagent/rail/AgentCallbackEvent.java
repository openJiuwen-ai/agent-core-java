/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

/**
 * Agent callback event types for agent lifecycle.
 * <p>
 * Lifecycle Callbacks:
 * <ul>
 * <li>BEFORE_INVOKE: Before agent.invoke() starts</li>
 * <li>AFTER_INVOKE: After agent.invoke() completes</li>
 * </ul>
 * <p>
 * Model Interaction Callbacks:
 * <ul>
 * <li>BEFORE_MODEL_CALL: Before LLM is called</li>
 * <li>AFTER_MODEL_CALL: After LLM response is received</li>
 * <li>ON_MODEL_EXCEPTION: When LLM call raises</li>
 * </ul>
 * <p>
 * Tool Execution Callbacks:
 * <ul>
 * <li>BEFORE_TOOL_CALL: Before a tool is executed</li>
 * <li>AFTER_TOOL_CALL: After a tool execution completes</li>
 * <li>ON_TOOL_EXCEPTION: When tool execution raises</li>
 * </ul>
 * 
 * @since 0.1.7
 */
public enum AgentCallbackEvent {
    BEFORE_INVOKE("before_invoke"),
    AFTER_INVOKE("after_invoke"),
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

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return value;
    }
}
