// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.singleagent.rail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgentCallbackEvent}.
 */
class AgentCallbackEventTest {

    @Test
    void testEnumValues() {
        AgentCallbackEvent[] values = AgentCallbackEvent.values();
        assertThat(values).hasSize(10);
    }

    @Test
    void testBeforeInvokeValue() {
        assertThat(AgentCallbackEvent.BEFORE_INVOKE.getValue()).isEqualTo("before_invoke");
    }

    @Test
    void testAfterInvokeValue() {
        assertThat(AgentCallbackEvent.AFTER_INVOKE.getValue()).isEqualTo("after_invoke");
    }

    @Test
    void testBeforeTaskIterationValue() {
        assertThat(AgentCallbackEvent.BEFORE_TASK_ITERATION.getValue()).isEqualTo("before_task_iteration");
    }

    @Test
    void testAfterTaskIterationValue() {
        assertThat(AgentCallbackEvent.AFTER_TASK_ITERATION.getValue()).isEqualTo("after_task_iteration");
    }

    @Test
    void testBeforeModelCallValue() {
        assertThat(AgentCallbackEvent.BEFORE_MODEL_CALL.getValue()).isEqualTo("before_model_call");
    }

    @Test
    void testAfterModelCallValue() {
        assertThat(AgentCallbackEvent.AFTER_MODEL_CALL.getValue()).isEqualTo("after_model_call");
    }

    @Test
    void testOnModelExceptionValue() {
        assertThat(AgentCallbackEvent.ON_MODEL_EXCEPTION.getValue()).isEqualTo("on_model_exception");
    }

    @Test
    void testBeforeToolCallValue() {
        assertThat(AgentCallbackEvent.BEFORE_TOOL_CALL.getValue()).isEqualTo("before_tool_call");
    }

    @Test
    void testAfterToolCallValue() {
        assertThat(AgentCallbackEvent.AFTER_TOOL_CALL.getValue()).isEqualTo("after_tool_call");
    }

    @Test
    void testOnToolExceptionValue() {
        assertThat(AgentCallbackEvent.ON_TOOL_EXCEPTION.getValue()).isEqualTo("on_tool_exception");
    }

    @Test
    void testToStringMatchesValue() {
        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            assertThat(event.toString()).isEqualTo(event.getValue());
        }
    }

    @Test
    void testValueOf() {
        assertThat(AgentCallbackEvent.valueOf("BEFORE_INVOKE"))
                .isEqualTo(AgentCallbackEvent.BEFORE_INVOKE);
        assertThat(AgentCallbackEvent.valueOf("ON_TOOL_EXCEPTION"))
                .isEqualTo(AgentCallbackEvent.ON_TOOL_EXCEPTION);
    }
}
