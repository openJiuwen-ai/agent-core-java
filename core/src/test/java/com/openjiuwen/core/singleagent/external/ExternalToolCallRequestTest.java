/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalToolCallRequestTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void exposesToolCallIdToolNameAndStringArguments() {
        ExternalToolCallRequest request = new ExternalToolCallRequest(
                "call-1",
                "frontend_read_text_input",
                "{\"field_id\":\"name\"}"
        );

        assertThat(request.getToolCallId()).isEqualTo("call-1");
        assertThat(request.getToolName()).isEqualTo("frontend_read_text_input");
        assertThat(request.getArguments()).isEqualTo("{\"field_id\":\"name\"}");
    }

    @Test
    void normalizesNullArgumentsToEmptyString() {
        ExternalToolCallRequest request = new ExternalToolCallRequest("call-1", "frontend_read_text_input", null);

        assertThat(request.getArguments()).isEmpty();
    }

    @Test
    void rejectsBlankToolCallIdAndToolName() {
        assertThatThrownBy(() -> new ExternalToolCallRequest(" ", "frontend_read_text_input", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolCallId");

        assertThatThrownBy(() -> new ExternalToolCallRequest("call-1", " ", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
    }

    @Test
    void pendingStateExposesExternalToolCalls() {
        AssistantMessage assistantMessage = new AssistantMessage("pending");
        ToolCall pendingToolCall = ToolCall.builder()
                .id("call-1")
                .name("frontend_read_text_input")
                .arguments("{}")
                .build();
        ExternalToolCallRequest externalCall = new ExternalToolCallRequest(
                "call-1",
                "frontend_read_text_input",
                "{}"
        );

        ExternalToolPendingState state = new ExternalToolPendingState(
                assistantMessage,
                2,
                "read the input",
                List.of(pendingToolCall),
                List.of(externalCall)
        );

        assertThat(state.getAssistantMessage()).isSameAs(assistantMessage);
        assertThat(state.getIteration()).isEqualTo(2);
        assertThat(state.getOriginalQuery()).isEqualTo("read the input");
        assertThat(state.getPendingToolCalls()).containsExactly(pendingToolCall);
        assertThat(state.getExternalToolCalls()).containsExactly(externalCall);
    }

    @Test
    void pendingStateJsonDoesNotExposeLegacyExternalCallRequestsGetter() throws JsonProcessingException {
        ExternalToolPendingState state = new ExternalToolPendingState(
                new AssistantMessage("pending"),
                1,
                "read the input",
                List.of(),
                List.of(new ExternalToolCallRequest("call-1", "frontend_read_text_input", "{}"))
        );

        String json = JSON.writeValueAsString(state);

        assertThat(json).contains("externalToolCalls");
        assertThat(json).doesNotContain("externalCallRequests");
    }
}
