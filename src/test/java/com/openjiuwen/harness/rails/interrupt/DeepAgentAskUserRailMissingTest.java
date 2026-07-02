/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * <p>Mirrors Python's {@code test_hitl_ask_user_rail} in
 * {@code tests/unit_tests/harness/rails/test_deep_agent_ask_user.py}.</p>
 */
class DeepAgentAskUserRailMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void testHitlAskUserRail() throws Exception {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = askUserToolCall();

        InterruptResult firstResult = assertInstanceOf(InterruptResult.class,
                rail.resolveInterrupt(null, toolCall, null));
        AskUserRail.AskUserRequest request = assertInstanceOf(
                AskUserRail.AskUserRequest.class,
                firstResult.request());

        assertThat(request.getQuestions()).hasSize(1);
        assertThat(request.getQuestions().get(0))
                .containsEntry("header", "File")
                .containsEntry("question", "What is the filename?");
        assertThat(request.getPayloadSchema()).containsKey("properties");
        assertThat(payloadProperties(request)).containsKey("answers");

        RejectResult resumed = assertInstanceOf(RejectResult.class,
                rail.resolveInterrupt(
                        null,
                        toolCall,
                        Map.of("answers", Map.of("What is the filename?", "user_answer.txt"))));

        assertThat(resumed.toolResult()).asString()
                .contains("User has answered your questions:")
                .contains("\"What is the filename?\"=\"user_answer.txt\"");
    }

    private static ToolCall askUserToolCall() throws Exception {
        return ToolCall.builder()
                .id("ask_user_tool_call")
                .type("function")
                .name("ask_user")
                .arguments(OBJECT_MAPPER.writeValueAsString(Map.of(
                        "questions", List.of(Map.of(
                                "header", "File",
                                "question", "What is the filename?",
                                "options", List.of(Map.of(
                                        "label", "file1.txt",
                                        "description", "Default")))))))
                .index(0)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadProperties(AskUserRail.AskUserRequest request) {
        return (Map<String, Object>) request.getPayloadSchema().get("properties");
    }
}
