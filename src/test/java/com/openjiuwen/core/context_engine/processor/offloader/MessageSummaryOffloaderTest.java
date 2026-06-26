/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.offloader;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.context_engine.schema.OffloadMessage;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for adaptive summary offloading.
 *
 * <p>Mirrors Python's {@code MessageSummaryOffloader} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_summary_offloader.py}.</p>
 *
 * <p>Mirrors Python's related tests in
 * {@code tests/unit_tests/core/context_engine/test_message_summary_offloader.py}.</p>
 *
 * <p>Mirrors Python's supplemental tests in
 * {@code tests/unit_tests/core/context_engine/test_new_message_summary_offloader.py}.</p>
 */
class MessageSummaryOffloaderTest {

    @Test
    void invalidPositiveFieldsAreRejected() {
        MessageSummaryOffloaderConfig config = new MessageSummaryOffloaderConfig();
        assertThatThrownBy(() -> config.setLargeMessageThreshold(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setSummaryMaxTokens(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setStepSummaryMaxContextMessages(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setContentMaxCharsForCompression(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registeredProcessorNameIsAvailableAfterClassLoad() {
        new MessageSummaryOffloader(baseConfig());
        assertThat(ContextEngine.registeredProcessorTypes()).contains("MessageSummaryOffloader");
    }

    @Test
    void initWithDefaultConfigRetainsConfig() {
        MessageSummaryOffloaderConfig config = new MessageSummaryOffloaderConfig();

        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config, failingModel());

        assertThat(offloader.getSummaryConfig()).isSameAs(config);
        assertThat(config.getLargeMessageThreshold()).isEqualTo(1000);
        assertThat(config.getOffloadMessageType()).containsExactly("tool");
        assertThat(config.getModel()).isNull();
        assertThat(config.getModelClient()).isNull();
    }

    @Test
    void initWithCustomConfigRetainsModelConfigs() {
        ModelRequestConfig modelConfig = ModelRequestConfig.builder()
                .modelName("test-model")
                .temperature(0.7d)
                .build();
        ModelClientConfig modelClientConfig = ModelClientConfig.builder()
                .clientId("test-client")
                .clientProvider("OpenAI")
                .apiKey("test-key")
                .apiBase("http://test.api.com")
                .build();
        MessageSummaryOffloaderConfig config = new MessageSummaryOffloaderConfig();
        config.setLargeMessageThreshold(500);
        config.setOffloadMessageType(List.of("user", "assistant"));
        config.setModel(modelConfig);
        config.setModelClient(modelClientConfig);

        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config, failingModel());

        assertThat(offloader.getSummaryConfig()).isSameAs(config);
        assertThat(config.getModel()).isSameAs(modelConfig);
        assertThat(config.getModelClient()).isSameAs(modelClientConfig);
        assertThat(config.getOffloadMessageType()).containsExactly("user", "assistant");
        assertThat(config.getLargeMessageThreshold()).isEqualTo(500);
    }

    @Test
    void assistantWithToolCallsIsNeverOffloaded() {
        MessageSummaryOffloaderConfig config = baseConfig();
        config.setOffloadMessageType(List.of("assistant"));
        Model model = failingModel();
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config, model);
        SessionModelContext context = contextWith(offloader);
        AssistantMessage assistant = assistantToolCall("tc-1", "search", "{}");
        assistant.setContent("A".repeat(100));

        context.addMessages(List.of(assistant)).toCompletableFuture().join();

        assertThat(context.getMessages()).containsExactly(assistant);
        assertThat(context.getMessages().get(0)).isNotInstanceOf(OffloadMessage.class);
    }

    @Test
    void offloadMessageWithDifferentRolesPreservesRoleAndOriginal() {
        List<OffloadCase> cases = List.of(
                new OffloadCase(new UserMessage("User message"), "user", "Summarized user message"),
                new OffloadCase(new AssistantMessage("Assistant message"), "assistant", "Summarized assistant message"),
                new OffloadCase(new ToolMessage("Tool message", "tool-call-1"), "tool", "Summarized tool message")
        );

        for (OffloadCase item : cases) {
            List<List<BaseMessage>> calls = new ArrayList<>();
            MessageSummaryOffloader offloader = new MessageSummaryOffloader(
                    configForRoles(List.of(item.role())),
                    modelReturning(calls, jsonSummary(item.summary()))
            );
            SessionModelContext context = contextWith(offloader);

            BaseMessage result = offloader.offloadMessageAdaptive(item.message(), context, Map.of())
                    .toCompletableFuture()
                    .join();

            assertThat(result).isInstanceOf(OffloadMessage.class);
            assertThat(result.getRole()).isEqualTo(item.role());
            assertThat(result.getContentAsString()).contains(item.summary());
            assertThat(calls).hasSize(1);
            assertThat(calls.get(0).get(0).getContentAsString()).contains(item.message().getContentAsString());
            assertThat(reloadOriginal(context, (OffloadMessage) result))
                    .contains(item.message().getContentAsString());
        }
    }

    @Test
    void offloadMessageEmptyContentUsesSummaryAndStoresOriginal() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(
                configForRoles(List.of("user")),
                modelReturning(new ArrayList<>(), jsonSummary("Empty message summary"))
        );
        SessionModelContext context = contextWith(offloader);
        UserMessage original = new UserMessage("");

        BaseMessage result = offloader.offloadMessageAdaptive(original, context, Map.of())
                .toCompletableFuture()
                .join();

        assertThat(result).isInstanceOf(OffloadMessage.class);
        assertThat(result.getContentAsString()).contains("Empty message summary");
        assertThat(reloadOriginal(context, (OffloadMessage) result)).contains("\"content\":\"\"");
    }

    @Test
    void offloadMessagePreservesOriginalMessagesForReload() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(
                configForRoles(List.of("user")),
                modelReturning(new ArrayList<>(), jsonSummary("Summary"))
        );
        SessionModelContext context = contextWith(offloader);
        UserMessage original = new UserMessage("Original message content");

        BaseMessage result = offloader.offloadMessageAdaptive(original, context, Map.of())
                .toCompletableFuture()
                .join();

        assertThat(result).isInstanceOf(OffloadMessage.class);
        assertThat(reloadOriginal(context, (OffloadMessage) result)).contains("Original message content");
    }

    @Test
    void adaptiveCompressionOffloadsSummaryAndExplanation() {
        List<List<BaseMessage>> calls = new ArrayList<>();
        Model model = modelReturning(calls, """
                {"summary":"compact summary","offload_data_explanation":{"category":"raw tool data","description":"full rows omitted","inferability":"low"}}
                """);
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(baseConfig(), model);
        SessionModelContext context = contextWith(offloader);
        String original = "LONG_TOOL_RESPONSE ".repeat(20);

        context.addMessages(List.of(
                new UserMessage("Summarize the current tool result."),
                assistantToolCall("tc-1", "search", "{}")
        )).toCompletableFuture().join();
        context.addMessages(List.of(
                new ToolMessage(original, "tc-1")
        )).toCompletableFuture().join();

        BaseMessage result = context.getMessages().get(2);
        assertThat(result).isInstanceOf(OffloadMessage.class);
        assertThat(result).isInstanceOf(ToolMessage.class);
        assertThat(((ToolMessage) result).getToolCallId()).isEqualTo("tc-1");
        assertThat(result.getContentAsString())
                .contains("compact summary")
                .contains("[offloaded_info]")
                .contains("category: raw tool data")
                .contains("[[OFFLOAD:");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).get(0).getContentAsString())
                .contains("[Current step requirements]")
                .contains("Summarize the current tool result.")
                .contains("[Current tool call function call]")
                .contains("search");

        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        assertThat(reloader.reloadOriginalContextMessages(
                ((OffloadMessage) result).getOffloadHandle(),
                ((OffloadMessage) result).getOffloadType()))
                .contains("LONG_TOOL_RESPONSE");
    }

    @Test
    void nonStringContentIsMeasuredAndSerializedForCompression() {
        List<List<BaseMessage>> calls = new ArrayList<>();
        Model model = modelReturning(calls, "{\"summary\":\"map summary\",\"offload_data_explanation\":{}}");
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(baseConfig(), model);
        SessionModelContext context = contextWith(offloader);
        ToolMessage toolMessage = new ToolMessage("", "tc-map");
        toolMessage.setContent(Map.of("payload", "X".repeat(100)));

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-map", "load", "{}"),
                toolMessage
        )).toCompletableFuture().join();

        BaseMessage result = context.getMessages().get(2);
        assertThat(result).isInstanceOf(OffloadMessage.class);
        assertThat(result.getContentAsString()).contains("map summary");
        assertThat(calls.get(0).get(0).getContentAsString()).contains("\"payload\"");
    }

    @Test
    void invalidShortJsonResultFallsBackToPlainSummary() {
        Model model = modelReturning(new ArrayList<>(), "plain summary");
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(baseConfig(), model);
        SessionModelContext context = contextWith(offloader);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-plain", "tool", "{}"),
                new ToolMessage("X".repeat(200), "tc-plain")
        )).toCompletableFuture().join();

        BaseMessage result = context.getMessages().get(2);
        assertThat(result).isInstanceOf(OffloadMessage.class);
        assertThat(result.getContentAsString()).contains("plain summary");
    }

    @Test
    void contextOverflowRetriesWithSmartTruncatedContent() {
        MessageSummaryOffloaderConfig config = baseConfig();
        config.setContentMaxCharsForCompression(80);
        List<List<BaseMessage>> calls = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            calls.add(messages);
            if (count.getAndIncrement() == 0) {
                return CompletableFuture.failedFuture(new RuntimeException("maximum context length exceeded"));
            }
            return CompletableFuture.completedFuture(
                    new AssistantMessage("{\"summary\":\"after retry\",\"offload_data_explanation\":{}}"));
        });
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config, model);
        SessionModelContext context = contextWith(offloader);

        context.addMessages(List.of(
                new UserMessage("task"),
                assistantToolCall("tc-retry", "tool", "{}"),
                new ToolMessage("0123456789".repeat(40), "tc-retry")
        )).toCompletableFuture().join();

        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).get(0).getContentAsString()).contains(MessageSummaryOffloader.TRUNCATED_MARKER);
        assertThat(context.getMessages().get(2).getContentAsString()).contains("after retry");
    }

    @Test
    void preciseStepUsesRecentUserAndAssistantConversation() {
        List<List<BaseMessage>> calls = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            calls.add(messages);
            if (count.getAndIncrement() == 0) {
                return CompletableFuture.completedFuture(new AssistantMessage("precise user task"));
            }
            return CompletableFuture.completedFuture(
                    new AssistantMessage("{\"summary\":\"summary with precise step\",\"offload_data_explanation\":{}}"));
        });
        MessageSummaryOffloaderConfig config = baseConfig();
        config.setEnablePreciseStep(true);
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config, model);
        SessionModelContext context = contextWith(offloader);

        context.addMessages(List.of(
                new UserMessage("older request"),
                new AssistantMessage("older answer")
        )).toCompletableFuture().join();
        context.addMessages(List.of(
                assistantToolCall("tc-precise", "tool", "{}"),
                new ToolMessage("Y".repeat(200), "tc-precise")
        )).toCompletableFuture().join();

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).get(0).getContentAsString()).contains("Conversation context");
        assertThat(calls.get(1).get(0).getContentAsString()).contains("precise user task");
        assertThat(context.getMessages().get(3)).isInstanceOf(OffloadMessage.class);
    }

    private static MessageSummaryOffloaderConfig baseConfig() {
        MessageSummaryOffloaderConfig config = new MessageSummaryOffloaderConfig();
        config.setLargeMessageThreshold(10);
        config.setOffloadMessageType(List.of("tool"));
        config.setProtectedToolNames(List.of("reload_original_context_messages"));
        return config;
    }

    private static MessageSummaryOffloaderConfig configForRoles(List<String> roles) {
        MessageSummaryOffloaderConfig config = baseConfig();
        config.setOffloadMessageType(roles);
        return config;
    }

    private static SessionModelContext contextWith(MessageSummaryOffloader offloader) {
        return new SessionModelContext("ctx", "session", new ContextEngineConfig(), List.of(), List.of(offloader), null);
    }

    private static String reloadOriginal(SessionModelContext context, OffloadMessage message) {
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        return reloader.reloadOriginalContextMessages(message.getOffloadHandle(), message.getOffloadType());
    }

    private static String jsonSummary(String summary) {
        return "{\"summary\":\"" + summary + "\",\"offload_data_explanation\":{}}";
    }

    private static Model modelReturning(List<List<BaseMessage>> calls, String content) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            calls.add(messages);
            return CompletableFuture.completedFuture(new AssistantMessage(content));
        });
    }

    private static Model failingModel() {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            throw new AssertionError("model should not be invoked");
        });
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

    private record OffloadCase(BaseMessage message, String role, String summary) {
    }
}
