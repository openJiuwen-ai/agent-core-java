/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.CallbackDecorators;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code ToolCard}, {@code _ToolMeta}, and {@code Tool} in
 * {@code openjiuwen/core/foundation/tool/base.py}.
 */
class ToolTest {

    @AfterEach
    void clearFramework() {
        Tool.clearCallbackFramework();
    }

    @Test
    void toolCardDefaultsArePerInstanceAndToolInfoUsesInputParams() {
        ToolCard first = ToolCard.builder()
                .id("id-1")
                .name("search")
                .description("Search things")
                .inputParams(Map.of("type", "object"))
                .properties(Map.of("x", 1))
                .build();
        ToolCard second = new ToolCard();

        first.getInputParams().put("extra", true);

        assertThat(second.getInputParams()).isEmpty();
        assertThat(second.getProperties()).isEmpty();
        assertThat(first.getProperties()).containsEntry("x", 1);

        ToolInfo info = first.toolInfo();
        assertThat(info.getName()).isEqualTo("search");
        assertThat(info.getDescription()).isEqualTo("Search things");
        assertThat(info.getParameters()).containsEntry("type", "object").containsEntry("extra", true);
    }

    @Test
    void constructorRejectsMissingCardAndEmptyCardId() {
        assertThatThrownBy(() -> new EchoTool(null))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> {
                    BaseError baseError = (BaseError) error;
                    assertThat(baseError.getStatus()).isEqualTo(StatusCode.TOOL_CARD_INVALID);
                    assertThat(baseError.getParams()).containsEntry("reason", "card is None");
                });

        ToolCard emptyId = ToolCard.builder().id("").name("bad").build();
        assertThatThrownBy(() -> new EchoTool(emptyId))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> {
                    BaseError baseError = (BaseError) error;
                    assertThat(baseError.getStatus()).isEqualTo(StatusCode.TOOL_CARD_INVALID);
                    assertThat(baseError.getParams()).containsEntry("reason", "card is is None or empty");
                });
    }

    @Test
    void invokeEmitsLifecycleEventsAndAppliesTransformCallbacks() throws Exception {
        RecordingFramework framework = new RecordingFramework();
        framework.transformations.put(ToolCallEvents.TOOL_INVOKE_INPUT, kwargs -> {
            Map<String, Object> transformed = new LinkedHashMap<>(kwargs);
            transformed.put("inputs", Map.of("value", 7));
            transformed.put("kwargs", Map.of("bonus", 1));
            return transformed;
        });
        framework.transformations.put(ToolCallEvents.TOOL_INVOKE_OUTPUT, kwargs -> 99);
        Tool.setCallbackFramework(framework);

        Object result = new EchoTool(validCard()).invoke(Map.of("value", 2), Map.of("bonus", 3));

        assertThat(result).isEqualTo(99);
        assertThat(framework.events()).containsExactly(
                "transform:" + ToolCallEvents.TOOL_INVOKE_INPUT,
                "trigger:" + ToolCallEvents.TOOL_INVOKE_INPUT,
                "trigger:" + ToolCallEvents.TOOL_CALL_STARTED,
                "trigger:" + ToolCallEvents.TOOL_CALL_FINISHED,
                "transform:" + ToolCallEvents.TOOL_INVOKE_OUTPUT,
                "trigger:" + ToolCallEvents.TOOL_INVOKE_OUTPUT
        );
        assertThat(framework.triggered(ToolCallEvents.TOOL_CALL_FINISHED).kwargs())
                .containsEntry("tool_name", "echo")
                .containsEntry("tool_id", "tool-1")
                .containsEntry("result", 8);
        assertThat(framework.triggered(ToolCallEvents.TOOL_INVOKE_OUTPUT).kwargs())
                .containsEntry("result", 99);
    }

    @Test
    void invokeEmitsErrorEventAndReraisesOriginalException() {
        RecordingFramework framework = new RecordingFramework();
        Tool.setCallbackFramework(framework);

        assertThatThrownBy(() -> new FailingTool(validCard()).invoke(Map.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(framework.events()).contains(
                "trigger:" + ToolCallEvents.TOOL_CALL_STARTED,
                "trigger:" + ToolCallEvents.TOOL_CALL_ERROR
        );
        assertThat(framework.events()).doesNotContain("trigger:" + ToolCallEvents.TOOL_CALL_FINISHED);
        assertThat(framework.triggered(ToolCallEvents.TOOL_CALL_ERROR).kwargs().get("error"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void streamEmitsLifecycleAndPerChunkResultEvents() throws Exception {
        RecordingFramework framework = new RecordingFramework();
        framework.transformations.put(ToolCallEvents.TOOL_STREAM_OUTPUT, kwargs -> {
            Object result = kwargs.get("result");
            return result instanceof Integer value ? value * 10 : CallbackDecorators.TRANSFORM_NOOP;
        });
        Tool.setCallbackFramework(framework);

        Iterator<Object> iterator = new EchoTool(validCard()).stream(Map.of("value", 1), Map.of());
        List<Object> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }

        assertThat(chunks).containsExactly(10, 20);
        assertThat(framework.events()).contains(
                "trigger:" + ToolCallEvents.TOOL_CALL_STARTED,
                "trigger:" + ToolCallEvents.TOOL_RESULT_RECEIVED,
                "trigger:" + ToolCallEvents.TOOL_STREAM_OUTPUT,
                "trigger:" + ToolCallEvents.TOOL_CALL_FINISHED
        );
        assertThat(framework.triggered(ToolCallEvents.TOOL_RESULT_RECEIVED).kwargs())
                .containsEntry("result", 1);
    }

    private static ToolCard validCard() {
        return ToolCard.builder()
                .id("tool-1")
                .name("echo")
                .description("Echo values")
                .inputParams(Map.of("type", "object"))
                .build();
    }

    /**
     * Mirrors Python's test-local {@code _EchoTool} behavior in
     * {@code openjiuwen/core/foundation/tool/base.py}.
     */
    private static final class EchoTool extends Tool {
        private EchoTool(ToolCard card) {
            super(card);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Number value = (Number) inputs.getOrDefault("value", 0);
            Number bonus = (Number) kwargs.getOrDefault("bonus", 0);
            return value.intValue() + bonus.intValue();
        }

        @Override
        protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.<Object>of(1, 2).iterator();
        }
    }

    /**
     * Mirrors Python's tool error-path behavior in
     * {@code openjiuwen/core/foundation/tool/base.py}.
     */
    private static final class FailingTool extends Tool {
        private FailingTool(ToolCard card) {
            super(card);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw new IllegalStateException("boom");
        }

        @Override
        protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of().iterator();
        }
    }

    /**
     * Mirrors Python's callback payload observations for {@code _ToolMeta} in
     * {@code openjiuwen/core/foundation/tool/base.py}.
     */
    private record RecordedCall(String event, Object[] args, Map<String, Object> kwargs) {
    }

    /**
     * Mirrors Python's {@code Runner.callback_framework} collaborator in
     * {@code openjiuwen/core/foundation/tool/base.py}.
     */
    private static final class RecordingFramework implements DecoratorFramework {
        private final List<RecordedCall> calls = new ArrayList<>();
        private final Map<String, Function<Map<String, Object>, Object>> transformations = new LinkedHashMap<>();

        @Override
        public CallbackInfo registerSync(String event,
                                         Function<Map<String, Object>, Object> callback,
                                         int priority,
                                         boolean once,
                                         String namespace,
                                         Set<String> tags,
                                         List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler,
                                         int maxRetries,
                                         double retryDelay,
                                         Double timeout,
                                         String callbackType) {
            return null;
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            calls.add(new RecordedCall("trigger:" + event, args, new LinkedHashMap<>(kwargs)));
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            calls.add(new RecordedCall("transform:" + event, args, new LinkedHashMap<>(kwargs)));
            Function<Map<String, Object>, Object> transformation = transformations.get(event);
            return transformation == null ? CallbackDecorators.TRANSFORM_NOOP : transformation.apply(kwargs);
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of();
        }

        private List<String> events() {
            return calls.stream().map(RecordedCall::event).toList();
        }

        private RecordedCall triggered(String event) {
            return calls.stream()
                    .filter(call -> call.event().equals("trigger:" + event))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
