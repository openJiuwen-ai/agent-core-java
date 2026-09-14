/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for round-level fallback compression behavior.
 *
 * <p>Mirrors Python's {@code RoundLevelCompressor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/round_level_compressor.py}.</p>
 *
 * <p>Mirrors Python's related tests in
 * {@code tests/unit_tests/core/context_engine/test_round_level_compressor.py}.</p>
 */
class RoundLevelCompressorTest {

    @Test
    void triggerGetContextWindowUsesTriggerTotalTokens() {
        RoundLevelCompressorConfig config = new RoundLevelCompressorConfig();
        config.setTriggerTotalTokens(100);
        config.setTargetTotalTokens(50);
        RoundLevelCompressor compressor = new RoundLevelCompressor(config, null);
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(compressor), null);

        assertThat(compressor.triggerGetContextWindow(
                context,
                new ContextWindow(List.of(), List.of(new UserMessage("u")), List.of(), null),
                Map.of()).toCompletableFuture().join()).isFalse();

        assertThat(compressor.triggerGetContextWindow(
                context,
                new ContextWindow(List.of(), List.of(new UserMessage("u".repeat(330))), List.of(), null),
                Map.of()).toCompletableFuture().join()).isTrue();
    }

    @Test
    void streamsStateWhenRoundLevelCompressorTriggersOnGet() {
        RoundLevelCompressorConfig config = new RoundLevelCompressorConfig();
        config.setTriggerTotalTokens(1);
        config.setTargetTotalTokens(1);
        TestableRoundLevelCompressor compressor = new TestableRoundLevelCompressor(config);
        compressor.compressionResult = List.of(new UserMessage("compressed"));
        SessionModelContext context = new SessionModelContext(
                "ctx",
                "round-level-compressor-stream-session",
                new ContextEngineConfig(),
                List.of(new UserMessage("old request"), new AssistantMessage("old answer")),
                List.of(compressor),
                messages -> messages.stream().mapToInt(message -> message.getContentAsString().length()).sum());

        ContextWindow window = context.getContextWindow(List.of(), List.of(), null, null, Map.of())
                .toCompletableFuture()
                .join();

        assertThat(window.getContextMessages()).extracting(BaseMessage::getContentAsString)
                .containsExactly("compressed");
        List<Map<String, Object>> states = context.compressionHistory();
        assertThat(states).hasSizeGreaterThanOrEqualTo(2);
        assertThat(states.get(states.size() - 2))
                .containsEntry("status", "started")
                .containsEntry("phase", "get_context_window")
                .containsEntry("processor", "RoundLevelCompressor");
        Map<String, Object> completed = states.get(states.size() - 1);
        assertThat(completed)
                .containsEntry("status", "completed")
                .containsEntry("phase", "get_context_window")
                .containsEntry("processor", "RoundLevelCompressor");
        assertThat(String.valueOf(completed.get("summary"))).contains("modified 2 messages");
    }

    @Test
    void buildMemoryMessageReturnsPlainUserMessageWithCompressionLevel() {
        RoundLevelCompressorConfig config = new RoundLevelCompressorConfig();
        TestableRoundLevelCompressor compressor = new TestableRoundLevelCompressor(config);
        RoundLevelCompressor.CompressTarget target = new RoundLevelCompressor.CompressTarget(
                "block_1",
                "ongoing_react",
                0,
                0,
                List.of(new AssistantMessage("analysis state")),
                0,
                1,
                1);

        BaseMessage message = compressor.buildMemoryMessageForTest("User Requirements:\n- Keep intent.", target);

        assertThat(message).isInstanceOf(UserMessage.class);
        assertThat(message.getContentAsString())
                .startsWith(RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER)
                .contains("processor: RoundLevelCompressor")
                .contains("User Requirements");
        assertThat(message.getMetadata()).containsEntry("compress_level", 1);
    }

    @Test
    void onGetContextWindowReportsOriginalMessageRangeAndCompactSummary() {
        RoundLevelCompressorConfig config = new RoundLevelCompressorConfig();
        config.setTriggerTotalTokens(100);
        config.setTargetTotalTokens(50);
        TestableRoundLevelCompressor compressor = new TestableRoundLevelCompressor(config);
        compressor.compressionResult = List.of(new UserMessage(
                RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER + "\n"
                        + "processor: RoundLevelCompressor\n"
                        + "Summary:\ncompressed"));
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(compressor), null);
        ContextWindow window = new ContextWindow(
                List.of(),
                List.of(
                        new UserMessage("u".repeat(180)),
                        new AssistantMessage("a".repeat(180)),
                        new UserMessage("x".repeat(180)),
                        new AssistantMessage("y".repeat(180))),
                List.of(),
                null);

        SessionModelContext.ProcessResult result = compressor.onGetContextWindow(context, window, Map.of())
                .toCompletableFuture()
                .join();

        assertThat(result.event()).isNotNull();
        assertThat(result.event().messagesToModify()).containsExactly(0, 1, 2, 3);
        assertThat(result.event().compactSummary())
                .startsWith(RoundLevelCompressor.ROUND_LEVEL_FALLBACK_MARKER)
                .contains("compressed");
        assertThat(result.contextWindow().getContextMessages()).hasSize(1);
        assertThat(context.getMessages()).hasSize(1);
    }

    @Test
    void buildCompressionUserPromptIncludesOngoingAndCompletedRequirements() {
        RoundLevelCompressorConfig config = new RoundLevelCompressorConfig();
        TestableRoundLevelCompressor compressor = new TestableRoundLevelCompressor(config);
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(compressor), null);

        String prompt = compressor.buildCompressionUserPromptForTest(
                List.of(
                        new UserMessage("request"),
                        new AssistantMessage("working"),
                        new UserMessage("another request"),
                        new AssistantMessage("final answer")),
                List.of(
                        new RoundLevelCompressor.CompressTarget(
                                "block_1",
                                "ongoing_react",
                                0,
                                1,
                                List.of(new UserMessage("request"), new AssistantMessage("working")),
                                0,
                                1,
                                1),
                        new RoundLevelCompressor.CompressTarget(
                                "block_2",
                                "completed_react",
                                2,
                                3,
                                List.of(new UserMessage("another request"), new AssistantMessage("final answer")),
                                0,
                                1,
                                1)),
                context);

        assertThat(prompt)
                .contains("User Requirements")
                .contains("Final Result")
                .contains("Do not weaken or over-compress the user's original request");
    }

    @Test
    void protectsAssistantToolCallWhenToolResultIsOutsideTargetRange() {
        List<BaseMessage> messages = List.of(
                new UserMessage("question"),
                assistantToolCall("call-1"),
                new ToolMessage("tool result", "call-1"),
                new AssistantMessage("final"));

        assertThat(RoundLevelCompressor.protectToolCallBoundary(messages, 0, 1)).isEqualTo(0);
        assertThat(RoundLevelCompressor.protectToolCallBoundary(messages, 0, 3)).isEqualTo(3);
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

    private static final class TestableRoundLevelCompressor extends RoundLevelCompressor {
        private List<BaseMessage> compressionResult;

        private TestableRoundLevelCompressor(RoundLevelCompressorConfig config) {
            super(config, null);
        }

        @Override
        public String processorType() {
            return "RoundLevelCompressor";
        }

        @Override
        protected List<BaseMessage> compressUntilTarget(List<BaseMessage> contextMessages,
                                                        SessionModelContext context,
                                                        List<BaseMessage> systemMessages,
                                                        List<com.openjiuwen.core.foundation.tool.schema.ToolInfo> tools,
                                                        int keepRecent,
                                                        boolean force) {
            return compressionResult == null ? contextMessages : compressionResult;
        }

        private BaseMessage buildMemoryMessageForTest(String summary, RoundLevelCompressor.CompressTarget target) {
            return buildMemoryMessage(summary, target);
        }

        private String buildCompressionUserPromptForTest(List<BaseMessage> messages,
                                                         List<RoundLevelCompressor.CompressTarget> targets,
                                                         SessionModelContext context) {
            return buildCompressionUserPrompt(messages, targets, context, "phase_1", 300, 0, null, null);
        }
    }
}
