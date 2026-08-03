/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.AskUserRequest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class AskUserRailMessageTest {
    @Test
    void buildAskRequestFillsMessageFromQuestionsAndKeepsPayload() {
        String args = "{\"questions\":[{\"header\":\"Len\",\"question\":\"What is the max context length?\","
                + "\"options\":[{\"label\":\"8k\",\"description\":\"8k tokens\"},"
                + "{\"label\":\"32k\",\"description\":\"32k tokens\"}]}]}";
        ToolCall call = ToolCall.builder().id("call-1").name("ask_user").arguments(args).build();

        AskUserRequest request = AskUserRail.buildAskRequest(call);

        assertThat(request.getMessage()).isNotEqualTo("ask_user");
        assertThat(request.getMessage())
                .contains("[Len] What is the max context length?")
                .contains("- 8k: 8k tokens")
                .contains("- 32k: 32k tokens");
        assertThat(request.getQuestions()).hasSize(1);
        assertThat(request.getQuestions().get(0)).containsEntry("question", "What is the max context length?");
        assertThat(request.getInterruptId()).isEqualTo("call-1");
        assertThat(request.getPayloadSchema()).containsKey("properties");
    }

    @Test
    void formatMessageFromQuestionsJoinsMultipleQuestions() {
        List<Map<String, Object>> questions = List.of(
                Map.of("header", "A", "question", "First?", "options",
                        List.of(Map.of("label", "Yes", "description", "accept"))),
                Map.of("question", "Second?", "multi_select", true, "options",
                        List.of(Map.of("label", "X", "description", "opt-x"))));

        String message = AskUserRail.formatMessageFromQuestions(questions);

        assertThat(message).contains("[A] First?").contains("- Yes: accept")
                .contains("Second?").contains("- X: opt-x").contains("(multi-select)");
        assertThat(message).contains("\n\n");
    }

    @Test
    void formatMessageFromQuestionsReturnsEmptyWhenNoContent() {
        assertThat(AskUserRail.formatMessageFromQuestions(null)).isEmpty();
        assertThat(AskUserRail.formatMessageFromQuestions(List.of())).isEmpty();
    }

    @Test
    void extractQuestionsNormalizesPlainStringItems() {
        ToolCall call = ToolCall.builder().id("call-2").name("ask_user")
                .arguments("{\"questions\":[\"Q1\",\"Q2\"]}").build();

        List<Map<String, Object>> questions = AskUserRail.extractQuestions(AskUserRail.parseToolArgs(call));

        assertThat(questions).hasSize(2);
        assertThat(questions.get(0)).containsEntry("question", "Q1");
        assertThat(questions.get(1)).containsEntry("question", "Q2");
        assertThat(AskUserRail.formatMessageFromQuestions(questions)).isEqualTo("Q1\n\nQ2");
    }

    @Test
    void resolveInterruptRejectsWithFormattedAnswers() {
        AskUserRail rail = new AskUserRail();
        String args = "{\"questions\":[{\"header\":\"Name\",\"question\":\"Please provide your name\","
                + "\"options\":[{\"label\":\"A\",\"description\":\"Alice\"}]}]}";
        ToolCall call = ToolCall.builder().id("call-3").name("ask_user").arguments(args).build();

        InterruptDecision decision = rail.resolveInterrupt(null, call, "Alice");

        assertThat(decision).isInstanceOf(RejectResult.class);
        assertThat(((RejectResult) decision).getToolResult().toString())
                .contains("User has answered your questions")
                .contains("Please provide your name")
                .contains("Alice");
    }

    @Test
    void resolveInterruptWithoutUserInputReturnsAskUserRequestWithMessage() {
        AskUserRail rail = new AskUserRail();
        String args = "{\"questions\":[{\"header\":\"Price\",\"question\":\"Please confirm pricing\","
                + "\"options\":[{\"label\":\"Yes\",\"description\":\"confirm\"}]}]}";
        ToolCall call = ToolCall.builder().id("call-4").name("ask_user").arguments(args).build();

        InterruptDecision decision = rail.resolveInterrupt(null, call, null);

        assertThat(decision).isInstanceOf(InterruptResult.class);
        AskUserRequest request = (AskUserRequest) ((InterruptResult) decision).getRequest();
        assertThat(request.getMessage())
                .contains("[Price] Please confirm pricing")
                .contains("- Yes: confirm");
        assertThat(request.getQuestions().get(0)).containsEntry("question", "Please confirm pricing");
    }
}
