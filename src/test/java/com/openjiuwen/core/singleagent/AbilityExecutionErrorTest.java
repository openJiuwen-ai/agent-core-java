// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AbilityExecutionError}.
 */
class AbilityExecutionErrorTest {

    @Test
    void testConstructionWithMessage() {
        ToolMessage tm = ToolMessage.builder()
                .content("error content")
                .toolCallId("tc-1")
                .build();

        AbilityExecutionError error = new AbilityExecutionError(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR,
                "Tool failed",
                tm
        );

        assertThat(error.getMessage()).contains("Tool failed");
        assertThat(error.getToolMessage()).isEqualTo(tm);
        assertThat(error.getToolMessage().getToolCallId()).isEqualTo("tc-1");
    }

    @Test
    void testConstructionWithCause() {
        ToolMessage tm = ToolMessage.builder()
                .content("caused error")
                .toolCallId("tc-2")
                .build();
        RuntimeException cause = new RuntimeException("root cause");

        AbilityExecutionError error = new AbilityExecutionError(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR,
                "Tool failed with cause",
                cause,
                tm
        );

        assertThat(error.getMessage()).contains("Tool failed with cause");
        assertThat(error.getCause()).isEqualTo(cause);
        assertThat(error.getToolMessage().getContent()).isEqualTo("caused error");
    }

    @Test
    void testIsRuntimeException() {
        AbilityExecutionError error = new AbilityExecutionError(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR,
                "test",
                null
        );
        assertThat(error).isInstanceOf(RuntimeException.class);
    }
}
