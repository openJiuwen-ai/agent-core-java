/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for dialogue compression behavior.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/core/context_engine/processor/compressor/dialogue_compressor.py}.</p>
 *
 * <p>Mirrors Python's {@code TestDialogueCompressor} in
 * {@code tests/unit_tests/core/context_engine/test_dialogue_compressor.py}.</p>
 */
class DialogueCompressorTest {

    @Test
    void triggerUsesCharacterFallbackWithoutTokenCounter() {
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        config.setMessagesThreshold(100);
        config.setTokensThreshold(100);
        config.setKeepLastRound(false);
        DialogueCompressor compressor = new DialogueCompressor(config, null);
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(new AssistantMessage("A".repeat(180))), List.of(compressor), null);

        boolean triggered = compressor.triggerAddMessages(
                context,
                List.of(new AssistantMessage("B".repeat(180))),
                Map.of()).toCompletableFuture().join();

        assertThat(triggered).isTrue();
    }

    @Test
    void onAddMessagesReplacesFinishedRoundAndCarriesCompactSummaryAndUsage() {
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        config.setMessagesThreshold(2);
        config.setKeepLastRound(false);
        AssistantMessage response = new AssistantMessage("");
        response.setParserContent(Map.of("blocks", List.of(Map.of(
                "block_id", "react_1",
                "summary", "Final Result: X."))));
        response.setUsageMetadata(UsageMetadata.builder().inputTokens(4).outputTokens(5).totalTokens(9).build());
        DialogueCompressor compressor = new DialogueCompressor(config, modelReturning(response));
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(compressor), null);

        SessionModelContext.ProcessResult result = compressor.onAddMessages(
                context,
                List.of(
                        new UserMessage("Call the tool"),
                        assistantToolCall("tc-1"),
                        new ToolMessage("Tool result: " + "data ".repeat(300), "tc-1"),
                        new AssistantMessage("Based on the result, the answer is X.")),
                false,
                Map.of()).toCompletableFuture().join();

        assertThat(result.messages()).isEmpty();
        assertThat(result.event()).isNotNull();
        assertThat(result.event().messagesToModify()).isEqualTo(List.of(1, 2, 3));
        assertThat(result.event().compactSummary())
                .contains(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER)
                .contains("Final Result: X.");
        Map<?, ?> usage = (Map<?, ?>) result.event().compressionUsage();
        assertThat(((Number) usage.get("total_tokens")).longValue()).isEqualTo(9L);
        List<BaseMessage> updatedMessages = context.getMessages();
        assertThat(updatedMessages).hasSize(2);
        assertThat(updatedMessages.get(0).getContentAsString()).isEqualTo("Call the tool");
        assertThat(updatedMessages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(updatedMessages.get(1).getContentAsString())
                .startsWith(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER);
    }

    @Test
    void invalidJsonPayloadFallsBackToPlainContentSummary() {
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        config.setMessagesThreshold(2);
        config.setKeepLastRound(false);
        AssistantMessage response = new AssistantMessage("Fallback Final Result");
        DialogueCompressor compressor = new DialogueCompressor(config, modelReturning(response));
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(compressor), null);

        SessionModelContext.ProcessResult result = compressor.onAddMessages(
                context,
                List.of(
                        new UserMessage("Call the tool"),
                        assistantToolCall("tc-1"),
                        new ToolMessage("Tool result: " + "data ".repeat(300), "tc-1"),
                        new AssistantMessage("Done")),
                false,
                Map.of()).toCompletableFuture().join();

        assertThat(result.event()).isNotNull();
        assertThat(result.event().compactSummary()).contains("Fallback Final Result");
    }

    @Test
    void streamsStateWhenDialogueCompressorTriggers() {
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        config.setMessagesThreshold(2);
        config.setKeepLastRound(false);
        AssistantMessage response = new AssistantMessage("");
        response.setParserContent(Map.of("blocks", List.of(Map.of(
                "block_id", "react_1",
                "summary", "Final Result: X."))));
        DialogueCompressor compressor = new DialogueCompressor(config, modelReturning(response));
        SessionModelContext context = new SessionModelContext("ctx", "dialogue-compressor-stream-session",
                new ContextEngineConfig(), List.of(), List.of(compressor), DialogueCompressorTest::sumContentLength);

        context.addMessages(reactMessages("Tool result: " + "data ".repeat(300),
                "Based on the result, the answer is X.")).toCompletableFuture().join();

        List<Map<String, Object>> states = context.compressionHistory();
        assertThat(states).hasSizeGreaterThanOrEqualTo(2);
        assertThat(states.get(states.size() - 2))
                .containsEntry("status", "started")
                .containsEntry("processor", "DialogueCompressor");
        Map<String, Object> completed = states.get(states.size() - 1);
        assertThat(completed)
                .containsEntry("status", "completed")
                .containsEntry("processor", "DialogueCompressor");
        assertThat((String) completed.get("summary")).contains("modified 3 messages");
        assertThat((String) completed.get("compact_summary"))
                .contains(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER)
                .contains("Final Result: X.");
    }

    @Test
    void buildMemoryMessageFallsBackToPlainUserMessage() throws Exception {
        DialogueCompressor compressor = new DialogueCompressor(new DialogueCompressorConfig(), null);
        java.lang.reflect.Method method = DialogueCompressor.class.getDeclaredMethod("buildMemoryMessage", String.class);
        method.setAccessible(true);

        BaseMessage message = (BaseMessage) method.invoke(compressor,
                "User Requirements:\n- Keep details.\n\nFinal Result:\n- Done.");

        assertThat(message).isInstanceOf(UserMessage.class);
        assertThat(message.getContentAsString())
                .startsWith(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER)
                .contains("Final Result");
    }

    @Test
    void builtinCompressionPromptUsedAsSystemPrompt() {
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        config.setMessagesThreshold(2);
        config.setKeepLastRound(false);
        AssistantMessage response = new AssistantMessage("");
        response.setParserContent(Map.of("blocks", List.of(Map.of(
                "block_id", "react_1",
                "summary", "User Requirements:\n- Keep details.\n\nFinal Result:\n- Done."))));
        AtomicReference<List<BaseMessage>> capturedMessages = new AtomicReference<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.set(messages);
            return CompletableFuture.completedFuture(response);
        });
        DialogueCompressor compressor = new DialogueCompressor(config, model);
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(compressor), DialogueCompressorTest::sumContentLength);

        compressor.onAddMessages(context, reactMessages("tool output ".repeat(300), "final answer"),
                false, Map.of()).toCompletableFuture().join();

        assertThat(capturedMessages.get()).isNotNull();
        assertThat(capturedMessages.get().get(0).getContentAsString())
                .contains("Task Data Preservation Expert");
    }

    @Test
    void compressPairsFindUserToFinalAssistantRounds() {
        assertThat(DialogueCompressor.getCompressPairs(List.of(
                new UserMessage("u"),
                assistantToolCall("call"),
                new ToolMessage("r", "call"),
                new AssistantMessage("done")
        ))).containsExactly(new DialogueCompressor.CompressPair(0, 3));
    }

    private static List<BaseMessage> reactMessages(String toolContent, String finalAnswer) {
        return List.of(
                new UserMessage("Call the tool"),
                assistantToolCall("tc-1"),
                new ToolMessage(toolContent, "tc-1"),
                new AssistantMessage(finalAnswer));
    }

    private static int sumContentLength(List<BaseMessage> messages) {
        return messages.stream().mapToInt(message -> message.getContentAsString().length()).sum();
    }

    private static AssistantMessage assistantToolCall(String id) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .name("tool")
                        .type("function")
                        .arguments("{}")
                        .build()))
                .build();
    }

    private static Model modelReturning(AssistantMessage message) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(message));
    }
}
