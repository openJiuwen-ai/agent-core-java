/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.offloader;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.context_engine.schema.OffloadMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for tool-result budget offloading.
 *
 * <p>Mirrors Python's {@code ToolResultBudgetProcessor} in
 * {@code openjiuwen/core/context_engine/processor/offloader/tool_result_budget_processor.py}.</p>
 *
 * <p>Mirrors Python's related tests in
 * {@code tests/unit_tests/core/context_engine/test_tool_result_budget_processor.py}.</p>
 */
class ToolResultBudgetProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultConfigValuesMatchPython() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();

        assertThat(config.getTokensThreshold()).isEqualTo(50000);
        assertThat(config.getLargeMessageThreshold()).isEqualTo(10000);
        assertThat(config.getTrimSize()).isEqualTo(3000);
        assertThat(config.getToolNameAllowlist()).isNull();
        assertThat(config.getOffloadMessageType()).containsExactly("tool");
        assertThat(config.getOffloadFilePrefix()).isEqualTo("ToolResultBudgetProcessor");
        assertThat(config.getMessagesThreshold()).isNull();
        assertThat(config.getMessagesToKeep()).isNull();
    }

    @Test
    void customConfigValuesAreRetained() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();
        config.setTokensThreshold(500);
        config.setLargeMessageThreshold(30);
        config.setTrimSize(12);
        config.setToolNameAllowlist(List.of("grep", "read_file"));
        config.setOffloadFilePrefix("CustomPrefix");
        config.setMessagesThreshold(10);
        config.setMessagesToKeep(3);

        assertThat(config.getTokensThreshold()).isEqualTo(500);
        assertThat(config.getLargeMessageThreshold()).isEqualTo(30);
        assertThat(config.getTrimSize()).isEqualTo(12);
        assertThat(config.getToolNameAllowlist()).containsExactly("grep", "read_file");
        assertThat(config.getOffloadFilePrefix()).isEqualTo("CustomPrefix");
        assertThat(config.getMessagesThreshold()).isEqualTo(10);
        assertThat(config.getMessagesToKeep()).isEqualTo(3);
    }

    @Test
    void invalidPositiveFieldsAreRejected() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();

        assertThatThrownBy(() -> config.setTokensThreshold(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setLargeMessageThreshold(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setTrimSize(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setMessagesThreshold(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setMessagesToKeep(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonToolOffloadRolesAreRejected() {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();

        assertThatThrownBy(() -> config.setOffloadMessageType(List.of("assistant")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registeredProcessorNameIsAvailableAfterClassLoad() {
        new ToolResultBudgetProcessor(new ToolResultBudgetProcessorConfig());

        assertThat(ContextEngine.registeredProcessorTypes()).contains("ToolResultBudgetProcessor");
    }

    @Test
    void triggerReturnsFalseWhenRoundIsBelowBudget() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(1000, 10, 5));
        SessionModelContext context = contextWithoutProcessor(null);
        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-1", "grep", "{}"),
                new ToolMessage("x".repeat(30), "tc-1"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        boolean triggered = processor.triggerAddMessages(context, List.<BaseMessage>of(), Map.of())
                .toCompletableFuture().join();

        assertThat(triggered).isFalse();
    }

    @Test
    void triggerReturnsTrueWhenRoundExceedsBudgetAndHasCandidate() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(100, 50, 20));
        SessionModelContext context = contextWithoutProcessor(null);
        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-1", "grep", "{}"),
                new ToolMessage("x".repeat(600), "tc-1"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        boolean triggered = processor.triggerAddMessages(context, List.<BaseMessage>of(), Map.of())
                .toCompletableFuture().join();

        assertThat(triggered).isTrue();
    }

    @Test
    void addingMessagesOffloadsLargeToolResultToFilesystemWhenWorkspaceExists() throws Exception {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 20));
        SessionModelContext context = contextWith(processor, null, tempDir);
        String content = "UNIQUE_CONTENT_" + "x".repeat(500) + "_END_MARKER";

        context.addMessages(List.of(
                new UserMessage("Run grep"),
                assistantToolCall("tc-file", "grep", "{}"),
                new ToolMessage(content, "tc-file"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        BaseMessage result = context.getMessages().get(2);
        assertThat(result).isInstanceOf(OffloadMessage.class);
        assertThat(result).isInstanceOf(ToolMessage.class);
        assertThat(result.getContentAsString())
                .startsWith(ToolResultBudgetProcessor.PERSISTED_OUTPUT_TAG)
                .contains("Output too large (" + content.length() + " bytes).")
                .contains("Preview (first 20 chars):")
                .contains(content.substring(0, 20))
                .contains("[[OFFLOAD: handle=")
                .contains("type=filesystem")
                .contains(ToolResultBudgetProcessor.PERSISTED_OUTPUT_CLOSING_TAG);
        OffloadMessage marker = (OffloadMessage) result;
        assertThat(((ToolMessage) result).getToolCallId()).isEqualTo("tc-file");

        Path offloadFile = tempDir.resolve("context/session_context/offload/ToolResultBudgetProcessor_"
                + marker.getOffloadHandle() + ".json");
        assertThat(Files.exists(offloadFile)).isTrue();
        assertThat(Files.readString(offloadFile)).contains("UNIQUE_CONTENT_").contains("_END_MARKER");
    }

    @Test
    void reloadFromFilesystemFindsProcessorPrefixedFile() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 20));
        SessionModelContext.SysOperationPort sysOperation = path -> {
            try {
                return Files.exists(Path.of(path)) ? java.util.Optional.of(Files.readString(Path.of(path)))
                        : java.util.Optional.empty();
            } catch (java.io.IOException ex) {
                return java.util.Optional.empty();
            }
        };
        SessionModelContext context = contextWith(processor, null, tempDir, sysOperation);
        String content = "ORIGINAL_TOOL_CONTENT_" + "x".repeat(500) + "_END_MARKER";

        context.addMessages(List.of(
                new UserMessage("Run grep"),
                assistantToolCall("tc-reload", "grep", "{}"),
                new ToolMessage(content, "tc-reload"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        OffloadMessage marker = (OffloadMessage) context.getMessages().get(2);
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                .contains("ORIGINAL_TOOL_CONTENT_")
                .contains("_END_MARKER");
    }

    @Test
    void addingMessagesFallsBackToInMemoryWithoutWorkspace() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 20));
        SessionModelContext context = contextWith(processor, null);
        String content = "x".repeat(500);

        context.addMessages(List.of(
                new UserMessage("Read file"),
                assistantToolCall("tc-memory", "read_file", "{}"),
                new ToolMessage(content, "tc-memory"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        BaseMessage result = context.getMessages().get(2);
        assertThat(result).isInstanceOf(OffloadMessage.class);
        OffloadMessage marker = (OffloadMessage) result;
        assertThat(marker.getOffloadType()).isEqualTo("in_memory");
        assertThat(result.getContentAsString()).contains("type=in_memory").contains("path=None");
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                .contains(content);
    }

    @Test
    void multipleRoundsAreProcessedIndependently() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 10));
        SessionModelContext context = contextWith(processor, null);

        context.addMessages(List.of(
                new UserMessage("First task"),
                assistantToolCall("tc-r1", "grep", "{}"),
                new ToolMessage("a".repeat(400), "tc-r1"),
                new AssistantMessage("Round 1 done"),
                new UserMessage("Second task"),
                assistantToolCall("tc-r2", "grep", "{}"),
                new ToolMessage("b".repeat(400), "tc-r2"),
                new AssistantMessage("Round 2 done")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().stream().filter(OffloadMessage.class::isInstance)).hasSize(2);
    }

    @Test
    void offloadsLargestCandidateFirstUntilBudgetFits() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(110, 10, 8));
        SessionModelContext context = contextWith(processor, null);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("small", "grep", "{}"),
                new ToolMessage("s".repeat(120), "small"),
                assistantToolCall("large", "grep", "{}"),
                new ToolMessage("L".repeat(300), "large"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(4)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void keepsOffloadingUntilRoundBudgetFits() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(60, 10, 8));
        SessionModelContext context = contextWith(processor, null);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("first", "grep", "{}"),
                new ToolMessage("A".repeat(180), "first"),
                assistantToolCall("second", "grep", "{}"),
                new ToolMessage("B".repeat(180), "second"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isInstanceOf(OffloadMessage.class);
        assertThat(context.getMessages().get(4)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void allowlistedToolNameIsNotOffloaded() {
        ToolResultBudgetProcessorConfig config = baseConfig(50, 10, 10);
        config.setToolNameAllowlist(List.of("important_tool"));
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(config);
        SessionModelContext context = contextWith(processor, null);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-important", "important_tool", "{}"),
                new ToolMessage("X".repeat(500), "tc-important"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(processor.triggerAddMessages(context, List.<BaseMessage>of(), Map.of())
                .toCompletableFuture().join()).isFalse();
    }

    @Test
    void nonAllowlistedToolCanStillBeOffloaded() {
        ToolResultBudgetProcessorConfig config = baseConfig(50, 10, 10);
        config.setToolNameAllowlist(List.of("important_tool"));
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(config);
        SessionModelContext context = contextWith(processor, null);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-other", "grep", "{}"),
                new ToolMessage("X".repeat(500), "tc-other"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void alreadyOffloadedToolMessageIsSkipped() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(20, 1, 5));
        SessionModelContext context = contextWith(processor, null);
        com.openjiuwen.core.context_engine.schema.OffloadToolMessage offloaded =
                new com.openjiuwen.core.context_engine.schema.OffloadToolMessage(
                        "already", "handle", "in_memory", "tc-x");

        assertThat(processor.shouldOffloadMessage(offloaded, List.of(offloaded), context)).isFalse();
    }

    @Test
    void nonStringToolContentIsSkipped() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(20, 1, 5));
        SessionModelContext context = contextWith(processor, null);
        ToolMessage message = new ToolMessage("", "tc-map");
        message.setContent(Map.of("payload", "X".repeat(200)));

        assertThat(processor.shouldOffloadMessage(message, List.of(message), context)).isFalse();
    }

    @Test
    void shortToolMessageIsNotCandidate() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(20, 100, 5));
        SessionModelContext context = contextWith(processor, null);
        ToolMessage message = new ToolMessage("short", "tc-short");

        assertThat(processor.shouldOffloadMessage(message, List.of(message), context)).isFalse();
    }

    @Test
    void roundBudgetUsesTokenCounterWhenAvailable() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 5));
        ModelContext.TokenCounterPort tokenCounter = messages -> messages.size() * 80;
        SessionModelContext context = contextWith(processor, tokenCounter);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-1", "grep", "{}"),
                new ToolMessage("small but token-expensive", "tc-1"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void tokenCounterFailureFallsBackToEstimate() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 5));
        ModelContext.TokenCounterPort tokenCounter = messages -> {
            throw new RuntimeException("counter unavailable");
        };
        SessionModelContext context = contextWith(processor, tokenCounter);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-1", "grep", "{}"),
                new ToolMessage("x".repeat(300), "tc-1"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void persistedOutputWithoutMoreDoesNotUseEllipsisLine() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 500));

        String result = processor.buildPersistedOutputMessage(3, "handle", "abc", false);

        assertThat(result)
                .contains("Preview (first 3 chars):\nabc\n")
                .doesNotContain("\n...\n");
    }

    @Test
    void incompleteLatestRoundIsDetectedAndOffloaded() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 5));
        SessionModelContext context = contextWith(processor, null);

        context.addMessages(List.of(
                new UserMessage("unfinished"),
                assistantToolCall("tc-incomplete", "grep", "{}"),
                new ToolMessage("x".repeat(300), "tc-incomplete")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void compressionHistoryRecordsModifiedMessageIndex() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(baseConfig(50, 10, 5));
        SessionModelContext context = contextWith(processor, null);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-stream", "grep", "{}"),
                new ToolMessage("x".repeat(300), "tc-stream"),
                new AssistantMessage("done")
        )).toCompletableFuture().join();

        List<Map<String, Object>> states = context.compressionHistory();
        assertThat(states).hasSize(2);
        assertThat(states.get(0))
                .containsEntry("processor", "ToolResultBudgetProcessor")
                .containsEntry("phase", "add_messages")
                .containsEntry("status", "started");
        assertThat(states.get(0).get("before")).isNotNull();
        assertThat(states.get(1))
                .containsEntry("processor", "ToolResultBudgetProcessor")
                .containsEntry("phase", "add_messages")
                .containsEntry("status", "completed");
        assertThat(states.get(1).get("duration_ms")).isNotNull();
        assertThat(String.valueOf(states.get(1).get("summary"))).contains("modified 1 messages");
    }

    @Test
    void loadAndSaveStateAreStatelessLikePython() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(new ToolResultBudgetProcessorConfig());

        processor.loadState(Map.of("ignored", "value"));

        assertThat(processor.saveState()).isEmpty();
    }

    private static ToolResultBudgetProcessorConfig baseConfig(int tokensThreshold, int largeMessageThreshold,
                                                             int trimSize) {
        ToolResultBudgetProcessorConfig config = new ToolResultBudgetProcessorConfig();
        config.setTokensThreshold(tokensThreshold);
        config.setLargeMessageThreshold(largeMessageThreshold);
        config.setTrimSize(trimSize);
        return config;
    }

    private static SessionModelContext contextWith(ToolResultBudgetProcessor processor,
                                                   ModelContext.TokenCounterPort tokenCounter) {
        return contextWith(processor, tokenCounter, null);
    }

    private static SessionModelContext contextWith(ToolResultBudgetProcessor processor,
                                                   ModelContext.TokenCounterPort tokenCounter,
                                                   Path workspaceRoot) {
        return contextWith(processor, tokenCounter, workspaceRoot, null);
    }

    private static SessionModelContext contextWith(ToolResultBudgetProcessor processor,
                                                   ModelContext.TokenCounterPort tokenCounter,
                                                   Path workspaceRoot,
                                                   SessionModelContext.SysOperationPort sysOperation) {
        SessionModelContext.WorkspacePort workspace = workspaceRoot == null ? null : () -> workspaceRoot.toString();
        return new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(processor), tokenCounter, null, workspace, sysOperation, null, null);
    }

    private static SessionModelContext contextWithoutProcessor(ModelContext.TokenCounterPort tokenCounter) {
        return new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(), tokenCounter);
    }

    private static AssistantMessage assistantToolCall(String id, String name, String arguments) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("assistant")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .name(name)
                        .type("function")
                        .arguments(arguments)
                        .build()))
                .build();
    }
}
