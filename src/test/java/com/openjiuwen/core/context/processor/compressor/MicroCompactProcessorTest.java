/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

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
 * Focused parity tests for micro tool-result compaction.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/core/context_engine/processor/compressor/micro_compact_processor.py}.</p>
 *
 * <p>Mirrors Python's {@code TestMicroCompactProcessor} in
 * {@code tests/unit_tests/core/context_engine/test_micro_compact_processor.py}.</p>
 */
class MicroCompactProcessorTest {

    @Test
    void triggerAddMessagesClearsOldToolMessages() {
        MicroCompactProcessorConfig config = lowThresholdConfig(List.of("read_file1", "read_file2"), 1);
        SessionModelContext context = contextWith(new MicroCompactProcessor(config), List.of());

        context.addMessages(List.of(
                assistantToolCall("tc-1", "read_file1"),
                new ToolMessage("file-content-1", "tc-1"),
                assistantToolCall("tc-2", "read_file1"),
                new ToolMessage("file-content-2", "tc-2"),
                assistantToolCall("tc-3", "read_file1"),
                new ToolMessage("file-content-3", "tc-3"),
                assistantToolCall("tc-4", "read_file2"),
                new ToolMessage("file-content-4", "tc-4"),
                assistantToolCall("tc-5", "read_file2"),
                new ToolMessage("file-content-5", "tc-5"),
                assistantToolCall("tc-6", "read_file2"),
                new ToolMessage("file-content-6", "tc-6")
        )).toCompletableFuture().join();

        assertThat(toolContents(context.getMessages())).containsExactly(
                config.getClearedMarker(),
                config.getClearedMarker(),
                "file-content-3",
                config.getClearedMarker(),
                config.getClearedMarker(),
                "file-content-6"
        );
    }

    @Test
    void streamsStateWhenMicroCompactProcessorTriggers() {
        MicroCompactProcessorConfig config = lowThresholdConfig(List.of("read_file"), 1);
        SessionModelContext context = contextWith(new MicroCompactProcessor(config), List.of());

        context.addMessages(List.of(
                assistantToolCall("tc-1", "read_file"),
                new ToolMessage("old-content", "tc-1"),
                assistantToolCall("tc-2", "read_file"),
                new ToolMessage("new-content", "tc-2"),
                assistantToolCall("tc-3", "read_file"),
                new ToolMessage("newest-content", "tc-3")
        )).toCompletableFuture().join();

        List<Map<String, Object>> states = context.compressionHistory();
        assertThat(states).hasSizeGreaterThanOrEqualTo(2);
        assertThat(states.get(states.size() - 2))
                .containsEntry("processor", "MicroCompactProcessor")
                .containsEntry("status", "started");
        assertThat(states.get(states.size() - 1))
                .containsEntry("processor", "MicroCompactProcessor")
                .containsEntry("status", "completed");
        assertThat((String) states.get(states.size() - 1).get("summary")).contains("modified");
    }

    @Test
    void keepRecentPerToolIsAppliedPerToolName() {
        MicroCompactProcessorConfig config = lowThresholdConfig(List.of("read_file", "grep"), 1);
        SessionModelContext context = contextWith(new MicroCompactProcessor(config), List.of());

        context.addMessages(List.of(
                assistantToolCall("r1", "read_file"),
                new ToolMessage("read-1", "r1"),
                assistantToolCall("r2", "read_file"),
                new ToolMessage("read-2", "r2"),
                assistantToolCall("r3", "read_file"),
                new ToolMessage("read-3", "r3"),
                assistantToolCall("g1", "grep"),
                new ToolMessage("grep-1", "g1"),
                assistantToolCall("g2", "grep"),
                new ToolMessage("grep-2", "g2"),
                assistantToolCall("g3", "grep"),
                new ToolMessage("grep-3", "g3")
        )).toCompletableFuture().join();
        context.addMessages(new UserMessage("trigger")).toCompletableFuture().join();

        assertThat(toolContents(context.getMessages())).containsExactly(
                config.getClearedMarker(),
                config.getClearedMarker(),
                "read-3",
                config.getClearedMarker(),
                config.getClearedMarker(),
                "grep-3"
        );
    }

    @Test
    void keepRecentPerToolIsAppliedIndependentlyAcrossTools() {
        MicroCompactProcessorConfig config = lowThresholdConfig(List.of("read_file", "grep", "glob"), 2);
        SessionModelContext context = contextWith(new MicroCompactProcessor(config), List.of());

        context.addMessages(List.of(
                assistantToolCall("read-1", "read_file"),
                new ToolMessage("read-old", "read-1"),
                assistantToolCall("grep-1", "grep"),
                new ToolMessage("grep-old", "grep-1"),
                assistantToolCall("glob-1", "glob"),
                new ToolMessage("glob-newer", "glob-1"),
                assistantToolCall("read-2", "read_file"),
                new ToolMessage("read-newest", "read-2")
        )).toCompletableFuture().join();

        assertThat(toolContents(context.getMessages())).containsExactly(
                "read-old",
                "grep-old",
                "glob-newer",
                "read-newest"
        );
    }

    @Test
    void clearedMessagesAreNotReprocessedAndMetadataIsPreserved() {
        MicroCompactProcessorConfig config = lowThresholdConfig(List.of("read_file"), 1);
        ToolMessage oldCleared = new ToolMessage(config.getClearedMarker(), "tc-1");
        ToolMessage fresh = new ToolMessage("fresh-content", "tc-2");
        fresh.setMetadata(Map.of("context_message_id", "mid-2", "kept", true));
        SessionModelContext context = contextWith(new MicroCompactProcessor(config), List.of(
                assistantToolCall("tc-1", "read_file"),
                oldCleared,
                assistantToolCall("tc-2", "read_file"),
                fresh,
                assistantToolCall("tc-3", "read_file"),
                new ToolMessage("newest-content", "tc-3")
        ));

        context.compressContext(List.of("MicroCompactProcessor"), Map.of()).toCompletableFuture().join();

        List<ToolMessage> tools = toolMessages(context.getMessages());
        assertThat(tools).hasSize(3);
        assertThat(tools.get(0).getContentAsString()).isEqualTo(config.getClearedMarker());
        assertThat(tools.get(1).getContentAsString()).isEqualTo(config.getClearedMarker());
        assertThat(tools.get(1).getToolCallId()).isEqualTo("tc-2");
        assertThat(tools.get(1).getMetadata()).containsEntry("kept", true);
        assertThat(tools.get(2).getContentAsString()).isEqualTo("newest-content");
    }

    @Test
    void forceCompressionIgnoresTriggerThresholdButKeepsRecentTail() {
        MicroCompactProcessorConfig config = new MicroCompactProcessorConfig();
        config.setTriggerThreshold(999);
        config.setCompactableToolNames(List.of("grep"));
        config.setKeepRecentPerTool(1);
        config.setClearedMarker("[CLEARED]");
        SessionModelContext context = contextWith(new MicroCompactProcessor(config), List.of(
                new UserMessage("search one"),
                assistantToolCall("call-1", "grep"),
                new ToolMessage("first grep result", "call-1", "grep"),
                new UserMessage("search two"),
                assistantToolCall("call-2", "grep"),
                new ToolMessage("second grep result", "call-2", "grep")
        ));

        Object result = context.compressContext(List.of("MicroCompactProcessor"), Map.of())
                .toCompletableFuture()
                .join();

        assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_COMPRESSED);
        assertThat(toolContents(context.getMessages())).containsExactly("[CLEARED]", "second grep result");
    }

    @Test
    void nonCompactableToolsAreNotCleared() {
        MicroCompactProcessorConfig config = lowThresholdConfig(List.of("read_file"), 1);
        SessionModelContext context = contextWith(new MicroCompactProcessor(config), List.of());

        context.addMessages(List.of(
                assistantToolCall("tc-1", "write_file"),
                new ToolMessage("write-1", "tc-1"),
                assistantToolCall("tc-2", "write_file"),
                new ToolMessage("write-2", "tc-2"),
                assistantToolCall("tc-3", "write_file"),
                new ToolMessage("write-3", "tc-3")
        )).toCompletableFuture().join();

        assertThat(toolContents(context.getMessages())).containsExactly("write-1", "write-2", "write-3");
    }

    private static SessionModelContext contextWith(MicroCompactProcessor processor, List<BaseMessage> history) {
        return new SessionModelContext(
                "ctx",
                "session",
                new ContextEngineConfig(),
                history,
                List.of(processor),
                messages -> messages.stream().mapToInt(message -> message.getContentAsString().length()).sum());
    }

    private static MicroCompactProcessorConfig lowThresholdConfig(List<String> toolNames, int keepRecentPerTool) {
        MicroCompactProcessorConfig config = new MicroCompactProcessorConfig();
        config.setTriggerThreshold(1);
        config.setCompactableToolNames(toolNames);
        config.setKeepRecentPerTool(keepRecentPerTool);
        return config;
    }

    private static AssistantMessage assistantToolCall(String id, String name) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .name(name)
                        .type("function")
                        .arguments("")
                        .build()))
                .build();
    }

    private static List<ToolMessage> toolMessages(List<BaseMessage> messages) {
        return messages.stream()
                .filter(ToolMessage.class::isInstance)
                .map(ToolMessage.class::cast)
                .toList();
    }

    private static List<String> toolContents(List<BaseMessage> messages) {
        return toolMessages(messages).stream()
                .map(BaseMessage::getContentAsString)
                .toList();
    }
}
