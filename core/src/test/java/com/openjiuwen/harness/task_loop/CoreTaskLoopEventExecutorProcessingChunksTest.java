/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Issue #66: parallel {@code __interaction__} members must collapse into one
 * {@code TASK_INTERACTION} chunk ({@code data:[N]}), otherwise TaskScheduler's
 * first-chunk break drops sibling mirrors from the wire.
 */
class CoreTaskLoopEventExecutorProcessingChunksTest {

    @Test
    void processingChunksMergesParallelInteractionsIntoSingleTaskInteraction() {
        List<OutputSchema> streamChunks = List.of(
                interaction("call_00", "DeepSeek V3 API pricing"),
                interaction("call_01", "Qwen-Max API pricing"),
                interaction("call_02", "Doubao-pro API pricing"));

        List<ControllerOutputChunk> chunks = CoreTaskLoopEventExecutor
                .processingChunks(Map.of("stream_chunks", streamChunks), "task-1");

        assertThat(chunks).hasSize(1);
        ControllerOutputChunk chunk = chunks.get(0);
        assertThat(chunk.getPayload()).isInstanceOf(ControllerOutputPayload.class);
        ControllerOutputPayload payload = (ControllerOutputPayload) chunk.getPayload();
        assertThat(payload.getType()).isEqualTo(EventType.TASK_INTERACTION.getValue());
        assertThat(payload.getData()).hasSize(3);
        assertThat(payload.getMetadata()).containsEntry("stream_kind", "inner_agent");

        List<String> messages = payload.getData().stream().map(frame -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = ((DataFrame.JsonDataFrame) frame).data();
            @SuppressWarnings("unchecked")
            Map<String, Object> interactionPayload = (Map<String, Object>) data.get("payload");
            return String.valueOf(interactionPayload.get("message"));
        }).toList();
        assertThat(messages).containsExactly("DeepSeek V3 API pricing", "Qwen-Max API pricing",
                "Doubao-pro API pricing");
    }

    @Test
    void processingChunksKeepsSingleInteractionAsOneElementDataArray() {
        List<ControllerOutputChunk> chunks = CoreTaskLoopEventExecutor.processingChunks(
                Map.of("stream_chunks", List.of(interaction("call_00", "only-one"))), "task-1");

        assertThat(chunks).hasSize(1);
        ControllerOutputPayload payload = (ControllerOutputPayload) chunks.get(0).getPayload();
        assertThat(payload.getType()).isEqualTo(EventType.TASK_INTERACTION.getValue());
        assertThat(payload.getData()).hasSize(1);
    }

    @Test
    void processingChunksLeavesNonInteractionAsTaskProcessing() {
        List<ControllerOutputChunk> chunks = CoreTaskLoopEventExecutor.processingChunks(
                Map.of("stream_chunks", List.of(new OutputSchema("llm_output", 0, Map.of("content", "hi")),
                        interaction("call_00", "need-input"))),
                "task-1");

        assertThat(chunks).hasSize(2);
        assertThat(((ControllerOutputPayload) chunks.get(0).getPayload()).getType())
                .isEqualTo(ControllerOutputPayload.TASK_PROCESSING);
        assertThat(((ControllerOutputPayload) chunks.get(1).getPayload()).getType())
                .isEqualTo(EventType.TASK_INTERACTION.getValue());
        assertThat(((ControllerOutputPayload) chunks.get(1).getPayload()).getData()).hasSize(1);
    }

    @Test
    void schedulerFirstChunkBreakStillPublishesAllParallelMembers() {
        // Issue #66 §4.2: TaskScheduler publishes the first TASK_INTERACTION and breaks.
        // After the fix, that first chunk already carries data:[N].
        List<ControllerOutputChunk> chunks = CoreTaskLoopEventExecutor.processingChunks(
                Map.of("stream_chunks", List.of(
                        interaction("call_00", "DeepSeek"),
                        interaction("call_01", "Qwen-Max"),
                        interaction("call_02", "Doubao-pro"))),
                "task-1");

        ControllerOutputChunk published = null;
        for (ControllerOutputChunk chunk : chunks) {
            ControllerOutputPayload payload = (ControllerOutputPayload) chunk.getPayload();
            if (EventType.TASK_INTERACTION.getValue().equals(payload.getType())) {
                published = chunk;
                break;
            }
        }

        assertThat(published).isNotNull();
        ControllerOutputPayload wire = (ControllerOutputPayload) published.getPayload();
        assertThat(wire.getData()).hasSize(3);
        assertThat(wire.getData().stream().map(frame -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = ((DataFrame.JsonDataFrame) frame).data();
            @SuppressWarnings("unchecked")
            Map<String, Object> interactionPayload = (Map<String, Object>) data.get("payload");
            return String.valueOf(interactionPayload.get("id"));
        }).toList()).containsExactly("call_00", "call_01", "call_02");
    }

    private static OutputSchema interaction(String toolCallId, String message) {
        return new OutputSchema("__interaction__", 0,
                Map.of("id", toolCallId, "message", message, "toolName", "search-agent"));
    }
}
