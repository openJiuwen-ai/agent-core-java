/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.harness.rails.CallbackContext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * <p>Mirrors Python's {@code test_hitl_ask_user_rail} in
 * {@code tests/unit_tests/harness/rails/test_deep_agent_ask_user.py}.</p>
 */
class DeepAgentAskUserRailMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final CallbackContext ABSENT_CONTEXT = null;

    @Test
    void multimodalAnswerResolvesWithoutStringifyingImages() throws Exception {
        List<Object> content = List.of(Map.of("type", "text", "text", "see new screenshot"),
                Map.of("type", "image", "source", Map.of("kind", "raw", "bytes", "aGk=")));
        RejectResult result = assertInstanceOf(RejectResult.class,
                new AskUserRail().resolveInterrupt(ABSENT_CONTEXT, askUserToolCall(), content));
        assertThat(result.toolResult()).asString().contains("see new screenshot").doesNotContain("aGk=");
    }

    @Test
    void imageOnlyAnswerResolves() throws Exception {
        RejectResult result = assertInstanceOf(RejectResult.class,
                new AskUserRail().resolveInterrupt(ABSENT_CONTEXT, askUserToolCall(), List.of(
                        Map.of("type", "image", "source", Map.of("kind", "raw", "bytes", "aGk=")))));
        assertThat(result.toolResult()).asString().contains("User has answered").doesNotContain("aGk=");
    }

    @Test
    void structuredMessagesResolveMultimodalAnswers() throws Exception {
        List<Object> content = List.of(Map.of("type", "text", "text", "new answer"),
                Map.of("type", "image", "source", Map.of("kind", "raw", "bytes", "aGk=")));
        UserMessage message = new UserMessage();
        message.setContent(content);
        for (Object input : List.of(message, Map.of("content", content))) {
            RejectResult result = assertInstanceOf(RejectResult.class,
                    new AskUserRail().resolveInterrupt(ABSENT_CONTEXT, askUserToolCall(), input));
            assertThat(result.toolResult()).asString().contains("new answer").doesNotContain("aGk=");
        }
    }

    @Test
    void emptyOrInvalidContentKeepsInterruption() throws Exception {
        for (Object input : List.of(List.of(), List.of(Map.of("type", "text", "text", "")),
                List.of(Map.of("type", "unknown")), List.of(Map.of("type", "image")),
                List.of(Map.of("type", "text", "text", "answer"),
                        Map.of("type", "video", "source", Map.of("kind", "raw", "bytes", "aGk="))))) {
            assertInstanceOf(InterruptResult.class,
                    new AskUserRail().resolveInterrupt(ABSENT_CONTEXT, askUserToolCall(), input));
        }
    }

    @Test
    void testHitlAskUserRail() throws Exception {
        AskUserRail rail = new AskUserRail();
        ToolCall toolCall = askUserToolCall();

        InterruptResult firstResult = assertInstanceOf(InterruptResult.class,
                rail.resolveInterrupt(ABSENT_CONTEXT, toolCall, null));
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
                        ABSENT_CONTEXT,
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
