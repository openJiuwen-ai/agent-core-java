/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor;

import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for context processor base behavior.
 *
 * <p>Mirrors Python's {@code ContextEvent}, {@code MetaContextProcessor}, and
 * {@code ContextProcessor} in
 * {@code openjiuwen/core/context_engine/processor/base.py}.</p>
 */
class ContextProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void eventCarriesPythonFieldsIncludingCompressionUsage() {
        ContextEvent event = new ContextEvent("compact", List.of(1, 3), "summary",
                Map.of("input_tokens", 7));

        assertThat(event.getEventType()).isEqualTo("compact");
        assertThat(event.messagesToModify()).containsExactly(1, 3);
        assertThat(event.compactSummary()).isEqualTo("summary");
        assertThat(event.getCompressionUsage()).containsEntry("input_tokens", 7);
    }

    @Test
    void defaultHooksAreNoopPassThroughs() {
        RecordingProcessor processor = new RecordingProcessor(Map.of("threshold", 10));
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig());
        List<BaseMessage> messages = List.of(new BaseMessage("user", "hello"));
        ContextWindow window = new ContextWindow(List.of(), messages, List.of(), null);

        assertThat(processor.processorType()).isEqualTo("RecordingProcessor");
        Map<String, Object> config = processor.config();
        assertThat(config).containsEntry("threshold", 10);
        assertThat(processor.triggerAddMessages(context, messages, Map.of()).toCompletableFuture().join()).isFalse();
        assertThat(processor.triggerGetContextWindow(context, window, Map.of()).toCompletableFuture().join())
                .isFalse();
        assertThat(processor.onAddMessages(context, messages, false, Map.of()).toCompletableFuture().join()
                .messages()).containsExactlyElementsOf(messages);
        assertThat(processor.onGetContextWindow(context, window, Map.of()).toCompletableFuture().join()
                .contextWindow()).isSameAs(window);
    }

    @Test
    void compressionUsageExtractsAndMergesUsageMetadata() {
        RecordingProcessor processor = new RecordingProcessor(null);
        AssistantMessage response = new AssistantMessage("answer");
        response.setUsageMetadata(UsageMetadata.builder()
                .inputTokens(3)
                .outputTokens(4)
                .totalTokens(7)
                .cacheTokens(1)
                .inputCost(0.1d)
                .outputCost(0.2d)
                .totalCost(0.3d)
                .modelName("m")
                .build());

        processor.recordCompressionUsage(response);
        processor.recordCompressionUsage(response);

        assertThat(processor.currentCompressionUsage())
                .containsEntry("calls", 2L)
                .containsEntry("input_tokens", 6L)
                .containsEntry("output_tokens", 8L)
                .containsEntry("total_tokens", 14L)
                .containsEntry("cache_tokens", 2L)
                .containsEntry("model_name", "m");
    }

    @Test
    void offloadMessagesStoresInMemoryAndCreatesRoleSpecificHint() {
        RecordingProcessor processor = new RecordingProcessor(null);
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig());

        BaseMessage hint = processor.offloadMessages("tool", "trimmed ",
                List.of(new BaseMessage("user", "large")),
                context,
                "handle-1",
                "in_memory",
                null,
                Map.of("tool_call_id", "call-1", "metadata", Map.of("source", "test")))
                .toCompletableFuture().join();

        assertThat(hint).isInstanceOf(ToolMessage.class);
        assertThat(hint.getContentAsString()).contains("[[OFFLOAD: handle=handle-1, type=in_memory]]");
        assertThat(hint.getMetadata()).containsEntry("offload_handle", "handle-1")
                .containsEntry("offload_type", "in_memory")
                .containsEntry("source", "test");
        assertThat(context.reloaderTool().name()).isEqualTo("reload_original_context_messages");
        SessionModelContext.ReloaderTool reloader = (SessionModelContext.ReloaderTool) context.reloaderTool();
        assertThat(reloader.reloadOriginalContextMessages("handle-1", "in_memory")).contains("large");
    }

    @Test
    void filesystemOffloadWritesAbsoluteFileAndReturnsPathHint() throws Exception {
        RecordingProcessor processor = new RecordingProcessor(null);
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig());
        Path offloadFile = tempDir.resolve("offload.json");

        BaseMessage hint = processor.offloadMessages("assistant", "trimmed ",
                List.of(new BaseMessage("user", "large")),
                context,
                "handle-2",
                "filesystem",
                offloadFile.toString(),
                Map.of())
                .toCompletableFuture().join();

        assertThat(hint).isInstanceOf(AssistantMessage.class);
        assertThat(hint.getContentAsString()).contains("[[OFFLOAD: type=filesystem, path=");
        assertThat(Files.readString(offloadFile)).contains("handle-2").contains("large");
    }

    @Test
    void filesystemOffloadFallbacksToInMemoryWhenPathIsRelative() {
        RecordingProcessor processor = new RecordingProcessor(null);
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig());

        BaseMessage hint = processor.offloadMessages("user", "trimmed ",
                List.of(new BaseMessage("user", "large")),
                context,
                "handle-3",
                "filesystem",
                "relative/offload.json",
                Map.of())
                .toCompletableFuture().join();

        assertThat(hint).isInstanceOf(com.openjiuwen.core.foundation.llm.schema.UserMessage.class);
        assertThat(hint.getContentAsString()).contains("[[OFFLOAD: handle=handle-3, type=in_memory]]");
    }

    @Test
    void apiRoundUsesCompletedApiRoundGrouping() {
        AssistantMessage assistantWithTool = AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(ToolCall.builder().id("call-1").name("search").arguments("{}").build()))
                .build();

        assertThat(ContextProcessor.apiRound(List.of(
                new BaseMessage("user", "q"),
                assistantWithTool
        ))).isFalse();
        assertThat(ContextProcessor.apiRound(List.of(
                new BaseMessage("user", "q"),
                assistantWithTool,
                new ToolMessage("result", "call-1"),
                new AssistantMessage("done")
        ))).isTrue();
    }

    private static final class RecordingProcessor extends ContextProcessor {
        private RecordingProcessor(Object config) {
            super(config);
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of();
        }
    }
}
