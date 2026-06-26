/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.offloader;

import com.openjiuwen.core.context_engine.context.ContextUtils;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.context_engine.schema.OffloadMessage;
import com.openjiuwen.core.context_engine.schema.OffloadMessages;
import com.openjiuwen.core.foundation.llm.Model;
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
 * Missing supplemental parity tests for adaptive message-summary offloading.
 *
 * <p>Mirrors Python's {@code TestMessageSummaryOffloader} in
 * {@code tests/unit_tests/core/context_engine/test_new_message_summary_offloader.py}.</p>
 */
class MessageSummaryOffloaderMissingTest {

    @Test
    void triggerAddMessagesOnlyForLargeToolMessages() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());
        SessionModelContext context = contextWith(offloader);
        ToolMessage smallTool = new ToolMessage("small", "tool-small");
        UserMessage largeUser = new UserMessage("x".repeat(100));
        ToolMessage largeTool = new ToolMessage("x".repeat(100), "tool-large");

        assertThat(offloader.triggerAddMessages(context, List.of(smallTool), Map.of()).toCompletableFuture().join())
                .isFalse();
        assertThat(offloader.triggerAddMessages(context, List.of(largeUser), Map.of()).toCompletableFuture().join())
                .isFalse();
        assertThat(offloader.triggerAddMessages(context, List.of(largeTool), Map.of()).toCompletableFuture().join())
                .isTrue();
    }

    @Test
    void getFunctionCallFromChainReturnsRawToolCall() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());
        ToolCall rawToolCall = ToolCall.builder()
                .id("call_123")
                .type("function")
                .name("get_weather")
                .arguments("{\"city\":\"Beijing\"}")
                .build();
        AssistantMessage assistant = AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(rawToolCall))
                .build();
        ToolMessage toolMessage = new ToolMessage("{}", "call_123");

        Object result = offloader.getFunctionCallFromChain(toolMessage, List.of(assistant));

        assertThat(result).isInstanceOf(ToolCall.class);
        ToolCall call = (ToolCall) result;
        assertThat(call.getId()).isEqualTo("call_123");
        assertThat(call.getName()).isEqualTo("get_weather");
        assertThat(call.getArguments()).isEqualTo("{\"city\":\"Beijing\"}");
    }

    @Test
    void preciseStepUsesRecentMessageLimit() {
        List<List<BaseMessage>> calls = new ArrayList<>();
        MessageSummaryOffloaderConfig config = adaptiveConfig();
        config.setEnablePreciseStep(true);
        config.setStepSummaryMaxContextMessages(2);
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config, modelReturning(calls, "latest task"));
        List<BaseMessage> messages = List.of(
                new UserMessage("first task"),
                new AssistantMessage("first answer"),
                new UserMessage("second task"),
                new AssistantMessage("second answer"));

        String result = offloader.getStepFromChainPrecise(messages);

        assertThat(result).isEqualTo("latest task");
        String prompt = calls.get(0).get(0).getContentAsString();
        assertThat(prompt).doesNotContain("first task").doesNotContain("first answer");
        assertThat(prompt).contains("second task").contains("second answer");
    }

    @Test
    void compressWithFallbackUsesConfiguredCharBudget() {
        MessageSummaryOffloaderConfig config = adaptiveConfig();
        config.setContentMaxCharsForCompression(50);
        List<List<BaseMessage>> calls = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            calls.add(messages);
            if (count.getAndIncrement() == 0) {
                return CompletableFuture.failedFuture(new RuntimeException("context length exceeded"));
            }
            return CompletableFuture.completedFuture(new AssistantMessage(
                    "{\"compression_strategy\":\"abstractive\",\"summary\":\"fallback summary\","
                            + "\"offload_data_explanation\":{}}"));
        });
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config, model);

        Map<String, Object> result = offloader.compressWithFallback("summarize",
                Map.of("name", "tool", "arguments", "{}"),
                "A".repeat(500));

        assertThat(result).containsEntry("summary", "fallback summary");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).get(0).getContentAsString())
                .contains(MessageSummaryOffloader.TRUNCATED_MARKER);
    }

    @Test
    void buildCompressionAttemptsRespectConfiguredLimits() {
        MessageSummaryOffloaderConfig config = adaptiveConfig();
        config.setContentMaxCharsForCompression(60);
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config, failingModel());

        List<String> attempts = offloader.buildCompressionAttempts("A".repeat(300));

        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(0)).isEqualTo("A".repeat(300));
        assertThat(attempts.get(1)).hasSizeLessThanOrEqualTo(60)
                .contains(MessageSummaryOffloader.TRUNCATED_MARKER);
        assertThat(attempts.get(2)).hasSizeLessThanOrEqualTo(30);
    }

    @Test
    void parseCompressionResultAcceptsEmbeddedJson() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());
        String payload = """
                ```json
                {
                  "compression_strategy": "extractive",
                  "summary": "important facts",
                  "offload_data_explanation": {
                    "category": "details",
                    "description": "full output",
                    "inferability": "low"
                  }
                }
                ```
                """;

        Map<String, Object> result = offloader.parseCompressionResult(payload);

        assertThat(result).containsEntry("summary", "important facts")
                .containsEntry("compression_strategy", "extractive");
    }

    @Test
    void parseCompressionResultRequiresSummary() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());

        assertThatThrownBy(() -> offloader.parseCompressionResult("{\"compression_strategy\":\"extractive\"}"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void offloadMessageAdaptiveKeepsOriginalMessageWhenFallbackIsNotShorter() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(
                adaptiveConfig(),
                modelReturning(new ArrayList<>(), "B".repeat(200)));
        SessionModelContext context = contextWith(offloader);
        context.addMessages(new UserMessage("Please inspect the tool output")).toCompletableFuture().join();
        ToolMessage toolMessage = new ToolMessage("A".repeat(120), "call_keep_original");

        BaseMessage result = offloader.offloadMessageAdaptive(toolMessage, context, Map.of()).toCompletableFuture().join();

        assertThat(result).isSameAs(toolMessage);
    }

    @Test
    void shouldOffloadMessageRespectsRoleAndSize() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());
        SessionModelContext context = contextWith(offloader);
        ToolMessage smallTool = new ToolMessage("small", "tool-small");
        UserMessage largeUser = new UserMessage("x".repeat(100));
        ToolMessage largeTool = new ToolMessage("x".repeat(100), "tool-large");
        BaseMessage offloadedTool = OffloadMessages.createOffloadMessage("tool", "x".repeat(100),
                "handle-123", "in_memory", Map.of("tool_call_id", "tool-offloaded"));

        assertThat(offloader.shouldOffloadMessage(smallTool, List.of(smallTool), context)).isFalse();
        assertThat(offloader.shouldOffloadMessage(largeUser, List.of(largeUser), context)).isFalse();
        assertThat(offloader.shouldOffloadMessage(largeTool, List.of(largeTool), context)).isTrue();
        assertThat(offloader.shouldOffloadMessage(offloadedTool, List.of(offloadedTool), context)).isFalse();
        assertThat(offloadedTool).isInstanceOf(OffloadMessage.class);
    }

    @Test
    void messageSizeUsesTokenCounterWhenAvailable() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());
        SessionModelContext context = new SessionModelContext(
                "ctx",
                "session",
                new ContextEngineConfig(),
                List.of(),
                List.of(offloader),
                messages -> 500);
        ToolMessage message = new ToolMessage("x".repeat(1000), "tool-1");

        assertThat(offloader.messageSize(message, context)).isEqualTo(500);
    }

    @Test
    void messageSizeFallsBackToCharDivision() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());
        ToolMessage message = new ToolMessage("x".repeat(99), "tool-1");

        assertThat(offloader.messageSize(message, contextWith(offloader))).isEqualTo(33);
    }

    @Test
    void smartTruncateContentPreservesHeadMiddleTailAndReturnsShortOriginal() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());
        String content = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(10);

        String truncated = offloader.smartTruncateContent(content, 100);

        assertThat(truncated).contains(MessageSummaryOffloader.TRUNCATED_MARKER);
        assertThat(truncated).startsWith("A").endsWith("Z");
        assertThat(offloader.smartTruncateContent("short content", 100)).isEqualTo("short content");
    }

    @Test
    void isContextOverflowErrorDetectsKeywords() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());

        assertThat(offloader.isContextOverflowError(new RuntimeException("context length exceeded"))).isTrue();
        assertThat(offloader.isContextOverflowError(new RuntimeException("token limit reached"))).isTrue();
        assertThat(offloader.isContextOverflowError(new RuntimeException("prompt is too long"))).isTrue();
        assertThat(offloader.isContextOverflowError(new RuntimeException("network timeout"))).isFalse();
        assertThat(offloader.isContextOverflowError(new RuntimeException("invalid api key"))).isFalse();
    }

    @Test
    void toolCallMatchesIdHandlesMapAndObjectFormats() {
        Map<String, Object> dictCall = Map.of("id", "call-123", "name", "test_tool");
        ToolCall objectCall = ToolCall.builder().id("call-456").name("test_tool").type("function").build();

        assertThat(ContextUtils.toolCallMatchesId(dictCall, "call-123")).isTrue();
        assertThat(ContextUtils.toolCallMatchesId(dictCall, "call-456")).isFalse();
        assertThat(ContextUtils.toolCallMatchesId(objectCall, "call-456")).isTrue();
        assertThat(ContextUtils.toolCallMatchesId(objectCall, "call-123")).isFalse();
    }

    @Test
    void getStepFromChainDefaultExtractsLastUserContentAndReturnsEmptyWhenMissing() {
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(adaptiveConfig(), failingModel());
        List<BaseMessage> messages = List.of(
                new UserMessage("First user message"),
                new AssistantMessage("Assistant response"),
                new UserMessage("Latest user message"),
                new AssistantMessage("Another response"));

        assertThat(offloader.getStepFromChainDefault(messages)).isEqualTo("Latest user message");
        assertThat(offloader.getStepFromChainDefault(List.of(
                new AssistantMessage("Assistant only"),
                new ToolMessage("Tool response", "tool-1")))).isEmpty();
    }

    private static MessageSummaryOffloaderConfig adaptiveConfig() {
        MessageSummaryOffloaderConfig config = new MessageSummaryOffloaderConfig();
        config.setLargeMessageThreshold(10);
        config.setSummaryMaxTokens(128);
        config.setStepSummaryMaxContextMessages(3);
        config.setContentMaxCharsForCompression(60);
        config.setOffloadMessageType(List.of("tool"));
        config.setProtectedToolNames(List.of("reload_original_context_messages"));
        return config;
    }

    private static SessionModelContext contextWith(MessageSummaryOffloader offloader) {
        return new SessionModelContext("ctx", "session", new ContextEngineConfig(), List.of(), List.of(offloader), null);
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
}
