// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for rail data classes: {@link InvokeInputs}, {@link ModelCallInputs},
 * {@link ToolCallInputs}, {@link RetryRequest}, {@link EventInputs}.
 */
class RailDataClassesTest {

    // ========== InvokeInputs ==========

    @Test
    void testInvokeInputsBuilder() {
        InvokeInputs inputs = InvokeInputs.builder()
                .query("test query")
                .conversationId("conv-123")
                .build();

        assertThat(inputs.getQuery()).isEqualTo("test query");
        assertThat(inputs.getConversationId()).isEqualTo("conv-123");
        assertThat(inputs.getResult()).isNull();
    }

    @Test
    void testInvokeInputsWithResult() {
        InvokeInputs inputs = InvokeInputs.builder()
                .query("q")
                .result(Map.of("output", "hello", "result_type", "answer"))
                .build();

        assertThat(inputs.getResult()).containsEntry("output", "hello");
        assertThat(inputs.getResult()).containsEntry("result_type", "answer");
    }

    @Test
    void testInvokeInputsNoArgsConstructor() {
        InvokeInputs inputs = new InvokeInputs();
        assertThat(inputs.getQuery()).isNull();
        assertThat(inputs.getConversationId()).isNull();
    }

    @Test
    void testInvokeInputsImplementsEventInputs() {
        InvokeInputs inputs = InvokeInputs.builder().build();
        assertThat(inputs).isInstanceOf(EventInputs.class);
    }

    // ========== ModelCallInputs ==========

    @Test
    void testModelCallInputsBuilder() {
        ModelCallInputs inputs = ModelCallInputs.builder()
                .messages(List.of("msg1", "msg2"))
                .build();

        assertThat(inputs.getMessages()).hasSize(2);
        assertThat(inputs.getTools()).isNull();
        assertThat(inputs.getResponse()).isNull();
    }

    @Test
    void testModelCallInputsDefaultMessages() {
        ModelCallInputs inputs = ModelCallInputs.builder().build();
        assertThat(inputs.getMessages()).isNotNull().isEmpty();
    }

    @Test
    void testModelCallInputsImplementsEventInputs() {
        assertThat(ModelCallInputs.builder().build()).isInstanceOf(EventInputs.class);
    }

    // ========== ToolCallInputs ==========

    @Test
    void testToolCallInputsBuilder() {
        ToolCall toolCall = ToolCall.builder()
                .id("tc-1")
                .name("add")
                .arguments("{\"a\": 1, \"b\": 2}")
                .build();

        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolCall(toolCall)
                .toolName("add")
                .toolArgs(Map.of("a", 1, "b", 2))
                .build();

        assertThat(inputs.getToolCall().getId()).isEqualTo("tc-1");
        assertThat(inputs.getToolName()).isEqualTo("add");
        assertThat(inputs.getToolResult()).isNull();
        assertThat(inputs.getToolMsg()).isNull();
    }

    @Test
    void testToolCallInputsWithResult() {
        ToolMessage msg = ToolMessage.builder()
                .content("3")
                .toolCallId("tc-1")
                .build();

        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolResult(3)
                .toolMsg(msg)
                .build();

        assertThat(inputs.getToolResult()).isEqualTo(3);
        assertThat(inputs.getToolMsg().getToolCallId()).isEqualTo("tc-1");
    }

    @Test
    void testToolCallInputsDefaultToolName() {
        ToolCallInputs inputs = ToolCallInputs.builder().build();
        assertThat(inputs.getToolName()).isEmpty();
    }

    @Test
    void testToolCallInputsImplementsEventInputs() {
        assertThat(ToolCallInputs.builder().build()).isInstanceOf(EventInputs.class);
    }

    // ========== RetryRequest ==========

    @Test
    void testRetryRequestDefaultDelay() {
        RetryRequest req = RetryRequest.builder().build();
        assertThat(req.getDelaySeconds()).isEqualTo(0.0);
    }

    @Test
    void testRetryRequestWithDelay() {
        RetryRequest req = RetryRequest.builder().delaySeconds(2.5).build();
        assertThat(req.getDelaySeconds()).isEqualTo(2.5);
    }

    @Test
    void testRetryRequestNoArgsConstructor() {
        RetryRequest req = new RetryRequest();
        assertThat(req.getDelaySeconds()).isEqualTo(0.0);
    }
}
