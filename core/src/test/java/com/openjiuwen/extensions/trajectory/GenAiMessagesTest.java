/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Shape tests for GenAI structured-message serialization.
 *
 * @since 0.1.18
 */
class GenAiMessagesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void serializesBaseMessagesWithTextParts() throws Exception {
        String json = GenAiMessages.serializeInput(List.of(
                new BaseMessage("system", "You are helpful"),
                new BaseMessage("user", "hello")));
        JsonNode messages = MAPPER.readTree(json);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).get("parts").get(0).get("type").asText()).isEqualTo("text");
        assertThat(messages.get(0).get("parts").get(0).get("content").asText())
                .isEqualTo("You are helpful");
        assertThat(messages.get(1).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).get("parts").get(0).get("content").asText())
                .isEqualTo("hello");
    }

    @Test
    void serializesPlainStringAsUserMessage() throws Exception {
        JsonNode messages = MAPPER.readTree(GenAiMessages.serializeInput(List.of("hi")));
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).get("parts").get(0).get("content").asText()).isEqualTo("hi");
    }

    @Test
    void serializesAssistantOutputWithReasoningAndToolCalls() throws Exception {
        AssistantMessage message = new AssistantMessage("final answer");
        message.setReasoningContent("thinking hard");
        message.setToolCalls(List.of(ToolCall.builder()
                .id("call-1").name("bash").arguments("{\"command\": \"ls\"}").build()));

        JsonNode output = MAPPER.readTree(GenAiMessages.serializeOutput(message))
                .get(0);
        assertThat(output.get("role").asText()).isEqualTo("assistant");
        JsonNode parts = output.get("parts");
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0).get("type").asText()).isEqualTo("reasoning");
        assertThat(parts.get(0).get("content").asText()).isEqualTo("thinking hard");
        assertThat(parts.get(1).get("type").asText()).isEqualTo("text");
        assertThat(parts.get(1).get("content").asText()).isEqualTo("final answer");
        assertThat(parts.get(2).get("type").asText()).isEqualTo("tool_call");
        assertThat(parts.get(2).get("id").asText()).isEqualTo("call-1");
        assertThat(parts.get(2).get("name").asText()).isEqualTo("bash");
        assertThat(parts.get(2).get("arguments").get("command").asText()).isEqualTo("ls");
    }

    @Test
    void serializesToolResultMessageWithResponsePart() throws Exception {
        BaseMessage toolMessage = new BaseMessage("tool", "{\"exit\": 0}", "bash");
        toolMessage.setMetadata(Map.of("tool_call_id", "call-1"));

        JsonNode output = MAPPER.readTree(GenAiMessages.serializeInput(List.of(toolMessage)))
                .get(0);
        assertThat(output.get("role").asText()).isEqualTo("tool");
        JsonNode part = output.get("parts").get(0);
        assertThat(part.get("type").asText()).isEqualTo("tool_call_response");
        assertThat(part.get("id").asText()).isEqualTo("call-1");
        assertThat(part.get("name").asText()).isEqualTo("bash");
        assertThat(part.get("response").get("exit").asInt()).isEqualTo(0);
    }

    @Test
    void serializesUsageAwareOutputKeepsFinishReasonIndependent() {
        AssistantMessage message = new AssistantMessage("ok");
        message.setUsageMetadata(UsageMetadata.builder()
                .inputTokens(11).outputTokens(5).totalTokens(16).build());
        String json = GenAiMessages.serializeOutput(message);
        assertThat(json).contains("assistant").doesNotContain("usage");
    }

    @Test
    void emptyInputSerializesToEmptyArray() {
        assertThat(GenAiMessages.serializeInput(null)).isEqualTo("[]");
        assertThat(GenAiMessages.serializeInput(List.of())).isEqualTo("[]");
        assertThat(GenAiMessages.serializeOutput(null)).isEqualTo("[]");
    }

    @Test
    void invalidJsonArgumentsStayRawStrings() throws Exception {
        AssistantMessage message = new AssistantMessage("");
        message.setToolCalls(List.of(ToolCall.builder()
                .id("call-2").name("write").arguments("not-json").build()));
        JsonNode part = MAPPER.readTree(GenAiMessages.serializeOutput(message))
                .get(0).get("parts").get(0);
        assertThat(part.get("arguments").asText()).isEqualTo("not-json");
    }
}
