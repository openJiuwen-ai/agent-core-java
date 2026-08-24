/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.CallbackUtils;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's context compression stream tests in
 * {@code tests/unit_tests/core/context_engine/test_context_compression_state_stream.py}.
 */
class ContextCompressionStateStreamPythonParityTest {
    private static final Pattern ISO_MILLIS_WITH_OFFSET = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}");

    @Test
    void activeCompressionStreamsStartedAndCompletedState() {
        RecordingSession session = new RecordingSession();
        SessionModelContext context = newContext(session, List.of(
                new UserMessage("a".repeat(80)),
                new UserMessage("b".repeat(80))
        ), List.of(new ReplacingCompressor()));

        Object result = await(context.compressContext(List.of("_ReplacingCompressor"), Map.of()));

        assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_COMPRESSED);
        List<Map<String, Object>> states = compressionStates(session);
        assertThat(states).extracting(state -> state.get("status")).containsExactly("started", "completed");

        Map<String, Object> started = states.get(0);
        assertThat(started).containsEntry("phase", "active_compress");
        assertThat(started).containsEntry("processor", "_ReplacingCompressor");
        assertThat(started).containsEntry("model", "test-compressor-model");
        Map<String, Object> before = stateMap(started, "before");
        assertThat((String) before.get("time")).matches(ISO_MILLIS_WITH_OFFSET);
        assertThat(before).containsEntry("messages", 2);
        assertThat(before).containsEntry("tokens", 40);
        assertThat(before).containsEntry("context_percent", 0);
        Map<String, Object> startedStatistic = stateMap(started, "statistic");
        assertThat(startedStatistic).containsEntry("total_messages", 2);
        assertThat(startedStatistic).containsEntry("total_tokens", 0);
        assertThat(startedStatistic).containsEntry("user_messages", 2);
        assertThat(started.get("after")).isNull();
        assertThat(started.get("saved")).isNull();
        assertThat(started.get("duration_ms")).isNull();

        Map<String, Object> completed = states.get(1);
        Map<String, Object> after = stateMap(completed, "after");
        assertThat((String) after.get("time")).matches(ISO_MILLIS_WITH_OFFSET);
        assertThat(after).containsEntry("messages", 1);
        assertThat(after).containsEntry("tokens", 2);
        assertThat(after).containsEntry("context_percent", 0);
        assertThat(stateMap(completed, "saved")).containsEntry("messages", 1).containsEntry("tokens", 38);
        Map<String, Object> completedStatistic = stateMap(completed, "statistic");
        assertThat(completedStatistic).containsEntry("total_messages", 1);
        assertThat(completedStatistic).containsEntry("total_tokens", 0);
        assertThat(completedStatistic).containsEntry("user_messages", 1);
        assertThat(completed).containsEntry("summary",
                "Compressed 2 -> 1 messages, ~40 -> ~2 tokens, saved ~38 tokens (95.0%), modified 2 messages");
        assertThat(completed.get("duration_ms")).isNotNull();
    }

    @Test
    void activeCompressionStreamsCompressionUsage() {
        RecordingSession session = new RecordingSession();
        SessionModelContext context = newContext(session, List.of(
                new UserMessage("a".repeat(80)),
                new UserMessage("b".repeat(80))
        ), List.of(new ReplacingUsageCompressor()));

        Object result = await(context.compressContext(List.of("_ReplacingUsageCompressor"), Map.of()));

        assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_COMPRESSED);
        List<Map<String, Object>> states = compressionStates(session);
        assertThat(states).extracting(state -> state.get("status")).containsExactly("started", "completed");
        Map<String, Object> usage = stateMap(states.get(1), "compression_usage");
        assertThat(usage).containsEntry("calls", 1);
        assertThat(usage).containsEntry("input_tokens", 100);
        assertThat(usage).containsEntry("output_tokens", 20);
        assertThat(usage).containsEntry("total_tokens", 120);
        assertThat(usage).containsEntry("cache_tokens", 10);
        assertThat(usage).containsEntry("model_name", "compress-model");
        assertThat(usage.get("details")).isEqualTo(List.of(Map.of(
                "model_name", "compress-model",
                "total_tokens", 120
        )));
    }

    @Test
    void activeCompressionReturnsNoopWhenProcessorDoesNotChangeContext() {
        RecordingSession session = new RecordingSession();
        SessionModelContext context = newContext(session, List.of(new UserMessage("unchanged")),
                List.of(new NoopCompressor()));

        Object result = await(context.compressContext(List.of("_NoopCompressor"), Map.of()));

        assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_NOOP);
        List<Map<String, Object>> states = compressionStates(session);
        assertThat(states).extracting(state -> state.get("status")).containsExactly("started", "noop");
        assertThat(stateMap(states.get(0), "before")).containsEntry("context_percent", 0);
    }

    @Test
    void contextPercentUsesModelContextWindowMapping() {
        RecordingSession session = new RecordingSession();
        ContextEngineConfig config = new ContextEngineConfig();
        config.setModelContextWindowTokens(Map.of("mapped-model", 200));
        SessionModelContext context = new SessionModelContext(
                "context-1",
                session.getSessionId(),
                config,
                List.of(new UserMessage("a".repeat(80))),
                List.of(new NoopCompressor()),
                null,
                session,
                null,
                null,
                null,
                null
        );

        Object result = await(context.compressContext(List.of("_NoopCompressor"), Map.of("model_name", "mapped-model")));

        assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_NOOP);
        Map<String, Object> before = stateMap(compressionStates(session).get(0), "before");
        assertThat(before).containsEntry("tokens", 20);
        assertThat(before).containsEntry("context_percent", 10);
    }

    @Test
    void stateCallbackFailureDoesNotBlockStreamEmit() {
        RecordingSession session = new RecordingSession();
        SessionModelContext context = newContext(session, List.of(new UserMessage("a".repeat(80))),
                List.of(new NoopCompressor()));
        CallbackUtils.setCallbackFramework(new ThrowingCallbackFramework());
        try {
            Object result = await(context.compressContext(List.of("_NoopCompressor"), Map.of()));

            assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_NOOP);
            assertThat(compressionStates(session)).extracting(state -> state.get("status"))
                    .containsExactly("started", "noop");
        } finally {
            CallbackUtils.resetFrameworkSupplier();
        }
    }

    private static SessionModelContext newContext(RecordingSession session, List<BaseMessage> historyMessages,
                                                  List<SessionModelContext.ContextProcessorPort> processors) {
        return new SessionModelContext(
                "context-1",
                session.getSessionId(),
                new ContextEngineConfig(),
                historyMessages,
                processors,
                null,
                session,
                null,
                null,
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> compressionStates(RecordingSession session) {
        return session.outputs.stream()
                .filter(output -> ContextProcessorStateRecorder.CONTEXT_COMPRESSION_STATE_TYPE.equals(output.getType()))
                .map(output -> (Map<String, Object>) output.getPayload())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stateMap(Map<String, Object> state, String key) {
        return (Map<String, Object>) state.get(key);
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class RecordingSession implements ContextProcessorStateRecorder.SessionStreamPort {
        private final List<OutputSchema> outputs = new ArrayList<>();

        String getSessionId() {
            return "session-1";
        }

        @Override
        public void writeStream(OutputSchema outputSchema) {
            outputs.add(outputSchema);
        }
    }

    private static class CompressConfig {
        public String getModel() {
            return "test-compressor-model";
        }
    }

    private static class ReplacingCompressor implements SessionModelContext.ContextProcessorPort {
        @Override
        public String processorType() {
            return "_ReplacingCompressor";
        }

        public Object config() {
            return new CompressConfig();
        }

        @Override
        public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messages,
                                                           Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            context.setMessages(List.of(new UserMessage("short")), true);
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                    new RecordingEvent(List.of(0, 1), "", null), List.of(), null));
        }
    }

    private static final class NoopCompressor extends ReplacingCompressor {
        @Override
        public String processorType() {
            return "_NoopCompressor";
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, messages, null));
        }
    }

    private static final class ReplacingUsageCompressor extends ReplacingCompressor {
        @Override
        public String processorType() {
            return "_ReplacingUsageCompressor";
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            context.setMessages(List.of(new UserMessage("short")), true);
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                    new RecordingEvent(List.of(0, 1), "", Map.of(
                            "calls", 1,
                            "input_tokens", 100,
                            "output_tokens", 20,
                            "total_tokens", 120,
                            "cache_tokens", 10,
                            "model_name", "compress-model",
                            "details", List.of(Map.of("model_name", "compress-model", "total_tokens", 120))
                    )),
                    List.of(),
                    null
            ));
        }
    }

    private record RecordingEvent(List<Integer> messagesToModify, String compactSummary,
                                  Object compressionUsage) implements SessionModelContext.ContextProcessorEventPort {
    }

    private static final class ThrowingCallbackFramework implements DecoratorFramework {
        @Override
        public CallbackInfo registerSync(String event, Function<Map<String, Object>, Object> callback, int priority,
                                         boolean once, String namespace, Set<String> tags, List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler, int maxRetries,
                                         double retryDelay, Double timeout, String callbackType) {
            return null;
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            throw new IllegalStateException("callback failed");
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            throw new IllegalStateException("callback failed");
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of();
        }
    }
}
