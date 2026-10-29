/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies the default compression prompt sent to the model keeps its structured preservation sections.
 */
class DialogueCompressorDefaultPromptStructureTest {

    @Test
    void defaultCompressionPromptCarriesStructuredPreservationSections() {
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        config.setMessagesThreshold(2);
        config.setKeepLastRound(false);
        AssistantMessage response = new AssistantMessage("");
        response.setParserContent(Map.of("blocks", List.of(Map.of(
                "block_id", "react_1",
                "summary", "Final Result: X."))));
        AtomicReference<List<BaseMessage>> capturedMessages = new AtomicReference<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.set(messages);
            return CompletableFuture.completedFuture(response);
        });
        DialogueCompressor compressor = new DialogueCompressor(config, model);
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(compressor), DialogueCompressorDefaultPromptStructureTest::sumContentLength);

        compressor.onAddMessages(context, reactMessages("tool output ".repeat(300), "final answer"),
                false, Map.of()).toCompletableFuture().join();

        assertThat(capturedMessages.get()).isNotNull();
        BaseMessage systemPrompt = capturedMessages.get().get(0);
        assertThat(systemPrompt).isInstanceOf(SystemMessage.class);
        String promptText = systemPrompt.getContentAsString();
        assertThat(promptText)
                .contains("Task Data Preservation Expert")
                .contains("[ANALYSIS PROGRESS]")
                .contains("[PENDING TASKS]")
                .contains("[OFFLOAD MARKERS]")
                .contains("[KEY REFERENCES]");
        assertThat(promptText).doesNotContain("{compression_target_tokens}");
    }

    private static List<BaseMessage> reactMessages(String toolContent, String finalAnswer) {
        return List.of(
                new UserMessage("Call the tool"),
                new UserMessage("Call the tool again"),
                assistantToolCall("tc-1"),
                new ToolMessage(toolContent, "tc-1"),
                new AssistantMessage(finalAnswer));
    }

    private static AssistantMessage assistantToolCall(String id) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(com.openjiuwen.core.foundation.llm.schema.ToolCall.builder()
                        .id(id)
                        .name("tool")
                        .type("function")
                        .arguments("{}")
                        .build()))
                .build();
    }

    private static int sumContentLength(List<BaseMessage> messages) {
        return messages.stream().mapToInt(message -> message.getContentAsString().length()).sum();
    }
}
