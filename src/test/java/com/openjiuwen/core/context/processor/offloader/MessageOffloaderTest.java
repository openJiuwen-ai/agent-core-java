/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.OffloadMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for message offload behavior.
 *
 * <p>Mirrors Python's {@code MessageOffloader} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_offloader.py}.</p>
 *
 * <p>Mirrors Python's related tests in
 * {@code tests/unit_tests/core/context_engine/test_message_offloader.py}.</p>
 */
class MessageOffloaderTest {

    @Test
    void invalidConfigRaisesContextExecutionError() {
        MessageOffloaderConfig trimConfig = new MessageOffloaderConfig();
        trimConfig.setTrimSize(500);
        trimConfig.setLargeMessageThreshold(500);
        assertThatThrownBy(() -> new MessageOffloader(trimConfig)).isInstanceOf(BaseError.class);

        MessageOffloaderConfig keepConfig = new MessageOffloaderConfig();
        keepConfig.setMessagesToKeep(20);
        keepConfig.setMessagesThreshold(20);
        assertThatThrownBy(() -> new MessageOffloader(keepConfig)).isInstanceOf(BaseError.class);
    }

    @Test
    void invalidConfigTrimSizeGreaterThanLargeMessageThresholdRaises() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setTrimSize(600);
        config.setLargeMessageThreshold(500);

        assertThatThrownBy(() -> new MessageOffloader(config)).isInstanceOf(BaseError.class);
    }

    @Test
    void invalidConfigMessagesToKeepGreaterThanThresholdRaises() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesToKeep(25);
        config.setMessagesThreshold(20);

        assertThatThrownBy(() -> new MessageOffloader(config)).isInstanceOf(BaseError.class);
    }

    @Test
    void validConfigCreatesContextSuccessfully() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesToKeep(10);
        config.setMessagesThreshold(20);
        config.setLargeMessageThreshold(500);
        config.setTrimSize(100);

        SessionModelContext context = contextWith(config, null);

        assertThat(context).isNotNull();
        assertThat(context.length()).isZero();
    }

    @Test
    void registeredProcessorNameIsAvailableAfterClassLoad() {
        assertThat(ContextEngine.registeredProcessorTypes()).contains("MessageOffloader");
    }

    @Test
    void belowMessagesToKeepDoesNotOffload() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(20);
        config.setMessagesToKeep(10);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(5);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new UserMessage("a"),
                new UserMessage("b"),
                new UserMessage("c"),
                new UserMessage("d"),
                new UserMessage("e")
        )).toCompletableFuture().join();

        assertThat(context.getMessages()).hasSize(5);
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);
    }

    @Test
    void aboveMessagesThresholdOffloadsAndReloadsOriginalContent() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(4);
        config.setTokensThreshold(100000);
        config.setLargeMessageThreshold(30);
        config.setTrimSize(10);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        SessionModelContext context = contextWith(config, null);
        String longContent = "x".repeat(100);

        context.addMessages(List.of(
                new UserMessage("u1"),
                new ToolMessage(longContent, "tc-1"),
                new UserMessage("u2"),
                new UserMessage("u3")
        )).toCompletableFuture().join();
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);

        context.addMessages(new UserMessage("u4")).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result).hasSize(5);
        assertThat(result.get(1)).isInstanceOf(OffloadMessage.class);
        OffloadMessage marker = (OffloadMessage) result.get(1);
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                .contains(longContent);
    }

    @Test
    void streamStateHistoryRecordsModifiedOffloadMessages() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(1);
        config.setTokensThreshold(100000);
        config.setLargeMessageThreshold(30);
        config.setTrimSize(10);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new ToolMessage("x".repeat(100), "tc-stream"),
                new UserMessage("trigger")
        )).toCompletableFuture().join();

        List<Map<String, Object>> states = context.compressionHistory();
        assertThat(states).hasSize(2);
        assertThat(states.get(0))
                .containsEntry("processor", "MessageOffloader")
                .containsEntry("phase", "add_messages")
                .containsEntry("status", "started");
        assertThat(states.get(0).get("before")).isNotNull();
        assertThat(states.get(1))
                .containsEntry("processor", "MessageOffloader")
                .containsEntry("phase", "add_messages")
                .containsEntry("status", "completed");
        assertThat(states.get(1).get("duration_ms")).isNotNull();
        assertThat(String.valueOf(states.get(1).get("summary"))).contains("modified 1 messages");
    }

    @Test
    void tokenThresholdUsesContextTokenCounter() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(100);
        config.setTokensThreshold(50);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(5);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        ModelContext.TokenCounterPort tokenCounter = messages -> 200;
        SessionModelContext context = contextWith(config, tokenCounter);

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage("x".repeat(20), "tc-1")
        )).toCompletableFuture().join();

        assertThat(context.getMessages()).anyMatch(OffloadMessage.class::isInstance);
        assertThat(context.getMessages().get(1).getContentAsString())
                .startsWith("x".repeat(5) + MessageOffloader.OMIT_STRING);
    }

    @Test
    void shortMessagesAreNotOffloaded() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(3);
        config.setLargeMessageThreshold(100);
        config.setTrimSize(10);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new ToolMessage("short", "tc-1"),
                new UserMessage("u")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(0).getContentAsString()).isEqualTo("short");
        assertThat(context.getMessages().get(0)).isNotInstanceOf(OffloadMessage.class);
    }

    @Test
    void offloadOnlyConfiguredRolesAndPreservesRecentMessages() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(2);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(5);
        config.setOffloadMessageType(List.of("user", "assistant"));
        config.setMessagesToKeep(1);
        config.setKeepLastRound(false);
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new UserMessage("U".repeat(50)),
                new AssistantMessage("A".repeat(50)),
                new ToolMessage("T".repeat(50), "tc-1")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(0)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(1)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(2)).isInstanceOf(ToolMessage.class);
        assertThat(result.get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(2).getContentAsString()).isEqualTo("T".repeat(50));
    }

    @Test
    void messagesToKeepLimitsOffloadRangeToOlderMessages() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(3);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(5);
        config.setOffloadMessageType(List.of("tool"));
        config.setMessagesToKeep(2);
        config.setKeepLastRound(false);
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new ToolMessage("A".repeat(50), "tc-1"),
                new ToolMessage("B".repeat(50), "tc-2"),
                new ToolMessage("C".repeat(50), "tc-3"),
                new ToolMessage("D".repeat(50), "tc-4")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(0)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(1)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(3)).isNotInstanceOf(OffloadMessage.class);
    }

    @Test
    void keepLastRoundPreservesFinalAssistantBoundary() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(2);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(5);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(true);
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new UserMessage("u1"),
                assistantToolCall("tc-1", "test-tool", "{}"),
                new ToolMessage("x".repeat(50), "tc-1"),
                new AssistantMessage("a2-final")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(2)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(3).getContentAsString()).isEqualTo("a2-final");
        assertThat(result.get(3)).isNotInstanceOf(OffloadMessage.class);
    }

    @Test
    void offloadTrimsContentAndReloadsOriginalContent() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(1);
        config.setLargeMessageThreshold(30);
        config.setTrimSize(10);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        SessionModelContext context = contextWith(config, null);
        String longContent = "a".repeat(200);

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(longContent, "tc-1")
        )).toCompletableFuture().join();

        BaseMessage offloadMessage = context.getMessages().get(1);
        assertThat(offloadMessage).isInstanceOf(OffloadMessage.class);
        assertThat(offloadMessage.getContentAsString())
                .startsWith("a".repeat(10))
                .contains("[[OFFLOAD:");
        OffloadMessage marker = (OffloadMessage) offloadMessage;
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                .contains(longContent);
    }

    @Test
    void offloadPreservesToolCallId() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(1);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(5);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        SessionModelContext context = contextWith(config, null);
        String fullContent = "Very long tool response: " + "x".repeat(100);

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(fullContent, "critical-tc-123")
        )).toCompletableFuture().join();

        BaseMessage offloadMessage = context.getMessages().get(1);
        assertThat(offloadMessage).isInstanceOf(ToolMessage.class);
        assertThat(((ToolMessage) offloadMessage).getToolCallId()).isEqualTo("critical-tc-123");
        OffloadMessage marker = (OffloadMessage) offloadMessage;
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                .contains("Very long tool response");
    }

    @Test
    void fullFlowAddMessagesTriggersOffloadAndReloadsContent() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(4);
        config.setTokensThreshold(100000);
        config.setLargeMessageThreshold(40);
        config.setTrimSize(15);
        config.setOffloadMessageType(List.of("tool", "user"));
        config.setMessagesToKeep(2);
        config.setKeepLastRound(true);
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new UserMessage("u1"),
                assistantToolCall("tc-1", "test-tool", ""),
                new ToolMessage("T".repeat(80), "tc-1"),
                new AssistantMessage("a2"),
                new UserMessage("U".repeat(80))
        )).toCompletableFuture().join();

        List<OffloadMessage> offloaded = context.getMessages().stream()
                .filter(OffloadMessage.class::isInstance)
                .map(OffloadMessage.class::cast)
                .toList();
        assertThat(offloaded).isNotEmpty();
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        for (OffloadMessage marker : offloaded) {
            assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                    .isNotBlank();
        }
    }

    @Test
    void multiRoundDialogueOffloadsOldToolsAndPreservesLatestFinalAssistant() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(10);
        config.setLargeMessageThreshold(30);
        config.setTrimSize(10);
        config.setOffloadMessageType(List.of("tool"));
        config.setMessagesToKeep(8);
        config.setKeepLastRound(true);
        SessionModelContext context = contextWith(config, null);
        List<BaseMessage> messages = new java.util.ArrayList<>();
        for (int round = 0; round < 3; round++) {
            messages.add(new UserMessage("user-round-" + round));
            messages.add(assistantToolCall("tc-" + round, "test-tool", ""));
            messages.add(new ToolMessage("LONG_TOOL_RESPONSE ".repeat(5), "tc-" + round));
            messages.add(new AssistantMessage("ai-final-" + round));
        }

        context.addMessages(messages).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result).hasSize(12);
        assertThat(result.stream()
                .filter(message -> "ai-final-2".equals(message.getContentAsString()))
                .findFirst()
                .orElseThrow()).isNotInstanceOf(OffloadMessage.class);
        List<OffloadMessage> offloaded = result.stream()
                .filter(OffloadMessage.class::isInstance)
                .map(OffloadMessage.class::cast)
                .toList();
        assertThat(offloaded).isNotEmpty();
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        assertThat(reloader.reloadOriginalContextMessages(
                offloaded.get(0).getOffloadHandle(), offloaded.get(0).getOffloadType()))
                .contains("LONG_TOOL_RESPONSE");
    }

    @Test
    void protectedToolByExactNameIsNotOffloaded() {
        MessageOffloaderConfig config = baseProtectedConfig(List.of("reload_original_context_messages"));
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-reload", "reload_original_context_messages", "{}"),
                new ToolMessage("X".repeat(200), "tc-reload")
        )).toCompletableFuture().join();

        BaseMessage toolMessage = context.getMessages().get(2);
        assertThat(toolMessage).isNotInstanceOf(OffloadMessage.class);
        assertThat(toolMessage.getContentAsString()).isEqualTo("X".repeat(200));
    }

    @Test
    void protectedToolPatternUsesToolArguments() {
        MessageOffloaderConfig config = baseProtectedConfig(List.of("read:path/to/*.py"));
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-1", "read", "{\"path\":\"path/to/main.py\"}"),
                new ToolMessage("X".repeat(200), "tc-1")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isNotInstanceOf(OffloadMessage.class);
    }

    @Test
    void protectedToolPatternMatchesMarkdownPath() {
        MessageOffloaderConfig config = baseProtectedConfig(List.of("view_file:*.md"));
        SessionModelContext context = contextWith(config, null);
        String longContent = "X".repeat(200);

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-1", "view_file", "{\"path\":\"README.md\"}"),
                new ToolMessage(longContent, "tc-1")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(context.getMessages().get(2).getContentAsString()).isEqualTo(longContent);
    }

    @Test
    void protectedToolPatternDoesNotMatchDifferentPath() {
        MessageOffloaderConfig config = baseProtectedConfig(List.of("read_file:*USER.md"));
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-data", "read_file", "{\"path\":\"data.txt\"}"),
                new ToolMessage("X".repeat(200), "tc-data")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void unprotectedToolIsOffloaded() {
        MessageOffloaderConfig config = baseProtectedConfig(List.of("reload_original_context_messages"));
        SessionModelContext context = contextWith(config, null);

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-other", "other_tool", "{}"),
                new ToolMessage("X".repeat(200), "tc-other")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void protectedToolQuestionMarkPatternMatchesSingleCharacter() {
        MessageOffloaderConfig config = baseProtectedConfig(List.of("read:file?.txt"));
        SessionModelContext context = contextWith(config, null);
        String longContent = "X".repeat(200);

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-1", "read", "{\"path\":\"file1.txt\"}"),
                new ToolMessage(longContent, "tc-1")
        )).toCompletableFuture().join();
        assertThat(context.getMessages().get(2)).isNotInstanceOf(OffloadMessage.class);

        context.addMessages(List.of(
                new UserMessage("u2"),
                assistantToolCall("tc-2", "read", "{\"path\":\"file12.txt\"}"),
                new ToolMessage(longContent, "tc-2")
        )).toCompletableFuture().join();
        assertThat(context.getMessages().get(5)).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void multipleProtectedPatternsCanBeConfiguredTogether() {
        MessageOffloaderConfig config = baseProtectedConfig(List.of(
                "reload_original_context_messages",
                "view_file:*.md",
                "read:*.py"));
        SessionModelContext context = contextWith(config, null);
        String longContent = "X".repeat(200);

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-reload", "reload_original_context_messages", "{}"),
                new ToolMessage(longContent, "tc-reload")
        )).toCompletableFuture().join();

        assertThat(context.getMessages().get(2)).isNotInstanceOf(OffloadMessage.class);
    }

    @Test
    void dictionaryToolCallArgumentFormatIsParsedForPatternMatching() {
        Map<String, Object> toolCall = Map.of(
                "id", "tc-1",
                "name", "read_file",
                "type", "function",
                "function", Map.of(
                        "name", "read_file",
                        "arguments", "{\"path\":\"config.json\"}"));

        assertThat(MessageOffloader.extractToolArgs(toolCall)).containsEntry("path", "config.json");
        assertThat(MessageOffloader.matchPattern(
                MessageOffloader.extractToolArgs(toolCall), "*.json")).isTrue();
    }

    private static SessionModelContext contextWith(MessageOffloaderConfig config,
                                                   ModelContext.TokenCounterPort tokenCounter) {
        MessageOffloader offloader = new MessageOffloader(config);
        return new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(offloader), tokenCounter);
    }

    private static MessageOffloaderConfig baseProtectedConfig(List<String> protectedToolNames) {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setMessagesThreshold(2);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(5);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        config.setProtectedToolNames(protectedToolNames);
        return config;
    }

    private static AssistantMessage assistantToolCall(String id, String name, String arguments) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("a")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .name(name)
                        .type("function")
                        .arguments(arguments)
                        .build()))
                .build();
    }
}
