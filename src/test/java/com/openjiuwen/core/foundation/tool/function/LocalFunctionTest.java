/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.function;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.callback.CallbackInfo;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code LocalFunction} behavior in
 * {@code openjiuwen/core/foundation/tool/function/function.py}.
 */
class LocalFunctionTest {

    @AfterEach
    void clearFramework() {
        LocalFunction.clearCallbackFramework();
    }

    @Test
    void constructorRejectsNullFunction() {
        ToolCard card = ToolCard.builder()
                .id("null_func")
                .name("null_func")
                .description("null func")
                .build();

        assertThrows(Throwable.class, () -> new LocalFunction(card, (java.util.function.Function<Map<String, Object>, Object>) null));
    }

    @Test
    void getFuncReturnsWrappedFunction() {
        ToolCard card = basicCard("echo");
        java.util.function.Function<Map<String, Object>, Object> func = inputs -> inputs.get("value");
        LocalFunction tool = new LocalFunction(card, func);

        assertSame(func, tool.getFunc());
    }

    @Test
    void invokeFormatsInputsWithSchemaDefaults() throws Exception {
        ToolCard card = ToolCard.builder()
                .id("with_default")
                .name("with_default")
                .description("with default")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string"),
                                "mode", Map.of("type", "string", "default", "text")
                        ),
                        "required", List.of("path")
                ))
                .build();
        LocalFunction tool = new LocalFunction(card,
                inputs -> inputs.get("path") + ":" + inputs.get("mode"));

        assertEquals("readme.md:text", tool.invoke(Map.of("path", "readme.md")));
    }

    @Test
    void invokeEmitsParseCallbacksAroundSchemaFormatting() throws Exception {
        RecordingFramework framework = new RecordingFramework();
        LocalFunction.setCallbackFramework(framework);
        Map<String, Object> schema = schemaWithDefaultMode();
        ToolCard card = ToolCard.builder()
                .id("parse_invoke")
                .name("parse_invoke")
                .description("parse invoke")
                .inputParams(schema)
                .build();
        LocalFunction tool = new LocalFunction(card,
                inputs -> inputs.get("path") + ":" + inputs.get("mode"));

        Object result = tool.invoke(Map.of("path", "readme.md"));

        assertEquals("readme.md:text", result);
        RecordedCall started = framework.triggered(ToolCallEvents.TOOL_PARSE_STARTED);
        assertEquals("parse_invoke", started.kwargs().get("tool_name"));
        assertEquals("parse_invoke", started.kwargs().get("tool_id"));
        assertEquals(Map.of("path", "readme.md"), started.kwargs().get("raw_inputs"));
        assertEquals(schema, started.kwargs().get("schema"));
        RecordedCall finished = framework.triggered(ToolCallEvents.TOOL_PARSE_FINISHED);
        assertEquals(Map.of("path", "readme.md", "mode", "text"), finished.kwargs().get("formatted_inputs"));
    }

    @Test
    void invokeAwaitsCompletionStageLikePythonCoroutine() throws Exception {
        LocalFunction tool = new LocalFunction(basicCard("async_like"),
                inputs -> CompletableFuture.completedFuture("done:" + inputs.get("value")));

        assertEquals("done:ok", tool.invoke(Map.of("value", "ok")));
    }

    @Test
    void invokeRejectsGeneratorLikeIteratorResult() {
        LocalFunction tool = new LocalFunction(basicCard("iterator"),
                inputs -> List.of("a", "b").iterator());

        Throwable thrown = assertThrows(Throwable.class, () -> tool.invoke(Map.of()));
        assertTrue(thrown.getMessage().contains("func can not be generator"));
    }

    @Test
    void streamYieldsIteratorResults() throws Exception {
        LocalFunction tool = new LocalFunction(basicCard("stream"),
                inputs -> List.of(inputs.get("a"), inputs.get("b")).iterator());

        Iterator<Object> iterator = tool.stream(Map.of("a", 1, "b", 2));
        List<Object> results = new ArrayList<>();
        while (iterator.hasNext()) {
            results.add(iterator.next());
        }

        assertEquals(List.of(1, 2), results);
    }

    @Test
    void streamEmitsParseCallbacksBeforeReturningIterator() throws Exception {
        RecordingFramework framework = new RecordingFramework();
        LocalFunction.setCallbackFramework(framework);
        ToolCard card = ToolCard.builder()
                .id("parse_stream")
                .name("parse_stream")
                .description("parse stream")
                .inputParams(schemaWithDefaultMode())
                .build();
        LocalFunction tool = new LocalFunction(card,
                inputs -> List.of(inputs.get("path"), inputs.get("mode")).iterator());

        Iterator<Object> iterator = tool.stream(Map.of("path", "readme.md"));
        List<Object> results = new ArrayList<>();
        while (iterator.hasNext()) {
            results.add(iterator.next());
        }

        assertEquals(List.of("readme.md", "text"), results);
        assertEquals("parse_stream", framework.triggered(ToolCallEvents.TOOL_PARSE_STARTED).kwargs().get("tool_id"));
        assertEquals(
                Map.of("path", "readme.md", "mode", "text"),
                framework.triggered(ToolCallEvents.TOOL_PARSE_FINISHED).kwargs().get("formatted_inputs")
        );
    }

    @Test
    void streamRejectsNonGeneratorFunction() {
        LocalFunction tool = new LocalFunction(basicCard("single"), inputs -> "result");

        Throwable thrown = assertThrows(Throwable.class, () -> tool.stream(Map.of()));
        assertTrue(thrown.getMessage().contains("func is not generator"));
    }

    private static ToolCard basicCard(String name) {
        return ToolCard.builder()
                .id(name)
                .name(name)
                .description(name)
                .build();
    }

    private static Map<String, Object> schemaWithDefaultMode() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string"),
                        "mode", Map.of("type", "string", "default", "text")
                ),
                "required", List.of("path")
        );
    }

    /**
     * Mirrors Python callback framework trigger observation for
     * {@code openjiuwen/core/foundation/tool/function/function.py}.
     */
    private record RecordedCall(String event, Object[] args, Map<String, Object> kwargs) {
    }

    /**
     * Mirrors Python's {@code trigger()} collaborator in
     * {@code openjiuwen/core/foundation/tool/function/function.py}.
     */
    private static final class RecordingFramework implements DecoratorFramework {
        private final List<RecordedCall> calls = new ArrayList<>();

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
            calls.add(new RecordedCall(event, args, new LinkedHashMap<>(kwargs)));
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of();
        }

        private RecordedCall triggered(String event) {
            return calls.stream()
                    .filter(call -> call.event().equals(event))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
