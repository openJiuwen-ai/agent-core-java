/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for context processor state recording.
 *
 * <p>Mirrors Python's {@code ContextProcessorStateRecorder} in
 * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
 */
class ContextProcessorStateRecorderTest {

    @Test
    void buildStateCreatesStartedMetricAndSummary() {
        ContextProcessorStateRecorder recorder = new ContextProcessorStateRecorder(
                "session-a", "ctx-a", () -> null, new RecordingTokenCounter(), 100, null);

        ContextProcessorStateRecorder.ContextCompressionState state = recorder.buildState(new ContextProcessorStateInput(
                "op-1",
                "started",
                "add_messages",
                "passive",
                new RecordingProcessor(),
                "processor_triggered",
                List.of(new BaseMessage("user", "question")),
                null,
                1.25d,
                null,
                null,
                List.of(),
                false,
                100,
                "",
                null
        ));

        assertThat(state.processor()).isEqualTo("compact");
        assertThat(state.model()).isEqualTo("model-a");
        assertThat(state.before().messages()).isEqualTo(1);
        assertThat(state.before().tokens()).isEqualTo(10);
        assertThat(state.before().contextPercent()).isEqualTo(10);
        assertThat(state.summary()).isEqualTo("Compressing 1 messages, ~10 tokens");
        assertThat(state.modelDump()).containsEntry("operation_id", "op-1");
    }

    @Test
    void buildStateIncludesSavedUsageDurationAndCompactSummary() {
        ContextProcessorStateRecorder recorder = new ContextProcessorStateRecorder(
                "session-a", "ctx-a", () -> null, new RecordingTokenCounter(), 100, null);

        ContextProcessorStateRecorder.ContextCompressionState state = recorder.buildState(new ContextProcessorStateInput(
                "op-2",
                "completed",
                "active_compress",
                "manual",
                new RecordingProcessor(),
                "processor_completed",
                List.of(new BaseMessage("user", "before"), new BaseMessage("assistant", "answer")),
                List.of(new BaseMessage("user", "after")),
                10.0d,
                10.125d,
                null,
                List.of(1, 2),
                true,
                100,
                "kept last turn",
                Map.of("calls", 1, "input_tokens", 7, "total_tokens", 9, "model_name", "m")
        ));

        assertThat(state.saved()).isNotNull();
        assertThat(state.durationMs()).isEqualTo(125);
        assertThat(state.compactSummary()).isEqualTo("kept last turn");
        assertThat(state.compressionUsage().calls()).isEqualTo(1);
        assertThat(state.compressionUsage().totalTokens()).isEqualTo(9);
        assertThat(state.summary()).contains("modified 2 messages");
        assertThat(state.modelDump()).containsKeys("compression_usage", "compact_summary", "duration_ms");
    }

    @Test
    @SuppressWarnings("unchecked")
    void emitRecordsHistoryTriggersCallbackAndWritesJsonPayload() {
        RecordingSession session = new RecordingSession();
        RecordingCallback callback = new RecordingCallback();
        ContextProcessorStateRecorder recorder = new ContextProcessorStateRecorder(
                "session-a", "ctx-a", () -> session, new RecordingTokenCounter(), 100, callback);
        ContextProcessorStateRecorder.ContextCompressionState state = recorder.buildState(new ContextProcessorStateInput(
                "op-3",
                "noop",
                "get_context_window",
                "passive",
                null,
                "processor_noop",
                List.of(new BaseMessage("user", "same")),
                List.of(new BaseMessage("user", "same")),
                20.0d,
                20.1d,
                null,
                List.of(),
                false,
                100,
                "",
                null
        ));

        recorder.emit(new Object(), state);

        assertThat(recorder.history()).hasSize(1);
        assertThat(callback.events).containsExactly("CONTEXT_COMPRESSION_STATE");
        assertThat(session.outputs).hasSize(1);
        OutputSchema output = session.outputs.get(0);
        assertThat(output.getType()).isEqualTo(ContextProcessorStateRecorder.CONTEXT_COMPRESSION_STATE_TYPE);
        Map<String, Object> payload = (Map<String, Object>) output.getPayload();
        assertThat(payload).containsEntry("operation_id", "op-3");
        assertThat(payload.get("after")).isInstanceOf(Map.class);
    }

    @Test
    void loadHistoryAndRecordRespectHistoryLimit() {
        ContextProcessorStateRecorder recorder = new ContextProcessorStateRecorder(
                "session-a", "ctx-a", () -> null, null, 2, null);
        recorder.loadHistory(List.of(
                Map.of("operation_id", "old-1"),
                Map.of("operation_id", "old-2"),
                Map.of("operation_id", "old-3")
        ));

        recorder.emit(null, recorder.buildState(new ContextProcessorStateInput(
                "new",
                "failed",
                "add_messages",
                "passive",
                null,
                "processor_error",
                List.of(new BaseMessage("user", "q")),
                null,
                1.0d,
                1.1d,
                "boom",
                List.of(),
                false,
                null,
                "",
                null
        )));

        assertThat(recorder.history()).extracting(item -> item.get("operation_id")).containsExactly("old-3", "new");
    }

    /**
     * Token counter test double.
     *
     * <p>Mirrors Python's {@code TokenCounter} dependency in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    private static final class RecordingTokenCounter implements ContextProcessorStateRecorder.TokenCounterPort {
        @Override
        public Integer countMessages(List<BaseMessage> messages) {
            return messages.size() * 10;
        }

        @Override
        public Integer count(Object content) {
            return String.valueOf(content).length();
        }
    }

    /**
     * Processor test double.
     *
     * <p>Mirrors Python's {@code ContextProcessor} dependency in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    private static final class RecordingProcessor implements ContextProcessorStateInput.ContextProcessorPort {
        @Override
        public String processorType() {
            return "compact";
        }

        public Object config() {
            return new ProcessorConfig();
        }
    }

    /**
     * Processor config test double.
     *
     * <p>Mirrors Python's dynamic processor config object in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public static final class ProcessorConfig {
        public String getModelName() {
            return "model-a";
        }
    }

    /**
     * Session stream test double.
     *
     * <p>Mirrors Python's {@code session.write_stream} dependency in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    private static final class RecordingSession implements ContextProcessorStateRecorder.SessionStreamPort {
        private final List<OutputSchema> outputs = new ArrayList<>();

        @Override
        public void writeStream(OutputSchema outputSchema) {
            outputs.add(outputSchema);
        }
    }

    /**
     * Callback test double.
     *
     * <p>Mirrors Python's callback framework dependency in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    private static final class RecordingCallback implements ContextProcessorStateRecorder.CallbackPort {
        private final List<String> events = new ArrayList<>();

        @Override
        public void trigger(String event, Object context, Object sessionRef, String sessionId, String contextId,
                            ContextProcessorStateRecorder.ContextCompressionState state) {
            events.add(event);
        }
    }
}
