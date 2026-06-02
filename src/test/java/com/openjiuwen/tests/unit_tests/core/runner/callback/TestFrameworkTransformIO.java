/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Framework transform IO test cases.
 *
 * <p>Mirrors Python's {@code test_framework_transform_io.py} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_transform_io}.</p>
 */
@DisplayName("Framework Transform IO Tests")
class TestFrameworkTransformIO {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(true, false);
    }

    private static Object[] argsFrom(Map<String, Object> kwargs) {
        Object raw = kwargs.get("_args");
        return raw instanceof Object[] args ? args : new Object[0];
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    @Test
    @DisplayName("test_transform_io_decorator_async_input_output")
    void testTransformIoDecoratorAsyncInputOutput() {
        Function<Map<String, Object>, Object> compute = kwargs -> kwargs.get("n");
        Function<Map<String, Object>, Object> wrapped = framework.transformIo(
                compute,
                kwargs -> new HashMap<>(Map.of("n", (int) kwargs.getOrDefault("n", 0) + 1)),
                result -> (int) result * 2);

        assertEquals(2, wrapped.apply(new HashMap<>(Map.of("n", 0))));
    }

    @Test
    @DisplayName("test_transform_io_decorator_input_only")
    void testTransformIoDecoratorInputOnly() {
        Function<Map<String, Object>, Object> fetch = kwargs -> kwargs.get("limit");
        Function<Map<String, Object>, Object> wrapped = framework.transformIo(
                fetch, kwargs -> new HashMap<>(Map.of("limit", 10)), null);

        assertEquals(10, wrapped.apply(new HashMap<>(Map.of("limit", 5))));
    }

    @Test
    @DisplayName("test_transform_io_decorator_output_only")
    void testTransformIoDecoratorOutputOnly() {
        Function<Map<String, Object>, Object> wrapped = framework.transformIo(
                kwargs -> 42, null, String::valueOf);

        assertEquals("42", wrapped.apply(new HashMap<>()));
    }

    @Test
    @DisplayName("test_transform_io_decorator_async_generator")
    void testTransformIoDecoratorAsyncGenerator() {
        List<Object> items = transformEach(List.of(1, 2, 3).iterator(), value -> (int) value * 2);

        assertEquals(List.of(2, 4, 6), items);
    }

    @Test
    @DisplayName("test_transform_io_decorator_sync_function")
    void testTransformIoDecoratorSyncFunction() {
        Function<Map<String, Object>, Object> wrapped = framework.transformIo(
                kwargs -> kwargs.getOrDefault("n", 0),
                kwargs -> new HashMap<>(Map.of("n", 3)),
                result -> (int) result * 3);

        assertEquals(9, wrapped.apply(new HashMap<>()));
    }

    @Test
    @DisplayName("test_transform_io_decorator_no_transform")
    void testTransformIoDecoratorNoTransform() {
        Function<Map<String, Object>, Object> wrapped = framework.transformIo(kwargs -> argsFrom(kwargs)[0], null, null);

        assertEquals(7, wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{7}))));
    }

    @Test
    @DisplayName("test_transform_io_by_events_input_output")
    void testTransformIoByEventsInputOutput() {
        framework.onTransform("input_transform",
                kwargs -> new CallbackFramework.BoundArgs(new Object[0], Map.of("limit", 10)), "normalize_input");
        framework.onTransform("output_transform", kwargs -> "result:" + kwargs.get("result"), "serialize_output");
        Function<Map<String, Object>, Object> fetchData = kwargs -> Map.of("count", kwargs.get("limit"));

        Object result = framework.transformIoByEvents(fetchData, "input_transform", "output_transform", "result")
                .apply(new HashMap<>(Map.of("_args", new Object[]{5})));

        assertEquals("result:{count=10}", result);
    }

    @Test
    @DisplayName("test_transform_io_by_events_input_only")
    void testTransformIoByEventsInputOnly() {
        framework.onTransform("input_event",
                kwargs -> new CallbackFramework.BoundArgs(new Object[0],
                        Map.of("n", (int) kwargs.getOrDefault("n", 0) + 1)), "add_one");
        Function<Map<String, Object>, Object> compute = kwargs -> kwargs.get("n");

        Object result = framework.transformIoByEvents(compute, "input_event", null, "result")
                .apply(new HashMap<>(Map.of("n", 0)));

        assertEquals(1, result);
    }

    @Test
    @DisplayName("test_transform_io_by_events_output_only")
    void testTransformIoByEventsOutputOnly() {
        framework.onTransform("output_event", kwargs -> (int) kwargs.get("result") * 2, "double");

        Object result = framework.transformIoByEvents(kwargs -> 21, null, "output_event", "result")
                .apply(new HashMap<>());

        assertEquals(42, result);
    }

    @Test
    @DisplayName("test_transform_io_by_events_no_callbacks_passthrough")
    void testTransformIoByEventsNoCallbacksPassthrough() {
        Object result = framework.transformIoByEvents(kwargs -> argsFrom(kwargs)[0],
                        "nonexistent_input", "nonexistent_output", "result")
                .apply(new HashMap<>(Map.of("_args", new Object[]{100})));

        assertEquals(100, result);
    }

    @Test
    @DisplayName("test_transform_io_by_events_last_callback_wins")
    void testTransformIoByEventsLastCallbackWins() {
        framework.onTransform("out", kwargs -> (int) kwargs.get("result") + 1, 0, "first");
        framework.onTransform("out", kwargs -> (int) kwargs.get("result") * 2, 10, "second");

        Object result = framework.transformIoByEvents(kwargs -> 5, null, "out", "result").apply(new HashMap<>());

        assertEquals(6, result);
    }

    @Test
    @DisplayName("test_transform_io_by_events_async_generator")
    void testTransformIoByEventsAsyncGenerator() {
        framework.onTransform("item", kwargs -> (int) kwargs.get("result") * 2, "double");
        Function<Map<String, Object>, Iterator<Object>> wrapped =
                framework.transformIoStreamByEvents(kwargs -> List.of((Object) 1, 2).iterator(), null, "item", "result");

        assertEquals(List.of(2, 4), collect(wrapped.apply(new HashMap<>())));
    }

    @Test
    @DisplayName("test_transform_io_by_events_custom_result_key")
    void testTransformIoByEventsCustomResultKey() {
        framework.onTransform("custom_out", kwargs -> (int) kwargs.get("value") * 3, "transform");

        Object result = framework.transformIoByEvents(kwargs -> 7, null, "custom_out", "value").apply(new HashMap<>());

        assertEquals(21, result);
    }

    @Test
    @DisplayName("test_framework_transform_io_callable")
    void testFrameworkTransformIoCallable() {
        Function<Map<String, Object>, Object> wrapped = framework.transformIo(
                kwargs -> kwargs.get("limit"),
                kwargs -> new HashMap<>(Map.of("limit", 10)),
                String::valueOf);

        assertEquals("10", wrapped.apply(new HashMap<>(Map.of("limit", 5))));
    }

    @Test
    @DisplayName("test_framework_transform_io_events")
    void testFrameworkTransformIoEvents() {
        framework.onTransform("in_ev", kwargs ->
                new CallbackFramework.BoundArgs(new Object[0], Map.of("x", (int) argsFrom(kwargs)[0] + 1)), "in_cb");
        framework.onTransform("out_ev", kwargs -> (int) kwargs.get("result") + 100, "out_cb");
        Function<Map<String, Object>, Object> compute = kwargs -> kwargs.get("x");

        Object result = framework.transformIoByEvents(compute, "in_ev", "out_ev", "result")
                .apply(new HashMap<>(Map.of("_args", new Object[]{1})));

        assertEquals(102, result);
    }

    @Test
    @DisplayName("test_framework_transform_io_events_prefer_over_callable")
    void testFrameworkTransformIoEventsPreferOverCallable() {
        framework.onTransform("ev", kwargs -> "from_event", "event_cb");

        Object result = framework.transformIoByEvents(kwargs -> "raw", null, "ev", "result").apply(new HashMap<>());

        assertEquals("from_event", result);
    }

    @Test
    @DisplayName("test_framework_transform_io_sync_generator_with_events")
    void testFrameworkTransformIoSyncGeneratorWithEvents() {
        framework.onTransform("item", kwargs -> (int) kwargs.get("result") * 2, "double");
        Function<Map<String, Object>, Iterator<Object>> wrapped =
                framework.transformIoStreamByEvents(kwargs -> List.of((Object) 10, 20).iterator(), null, "item", "result");

        assertEquals(List.of(20, 40), collect(wrapped.apply(new HashMap<>())));
    }

    @Test
    @DisplayName("test_transform_io_decorator_sync_generator_direct")
    void testTransformIoDecoratorSyncGeneratorDirect() {
        assertEquals(List.of(-1, -2), transformEach(List.of(1, 2).iterator(), value -> -(int) value));
    }

    @Test
    @DisplayName("test_transform_io_decorator_async_input_transform")
    void testTransformIoDecoratorAsyncInputTransform() {
        Function<Map<String, Object>, Object> wrapped = framework.transformIo(
                kwargs -> kwargs.get("n"),
                kwargs -> new HashMap<>(Map.of("n", (int) kwargs.getOrDefault("n", 0) + 1)),
                null);

        assertEquals(1, wrapped.apply(new HashMap<>(Map.of("n", 0))));
    }

    @Test
    @DisplayName("test_transform_io_by_events_input_tuple_return")
    void testTransformIoByEventsInputTupleReturn() {
        framework.onTransform("in", kwargs -> new CallbackFramework.BoundArgs(new Object[]{1, 2}, Map.of("a_key", 3)), "return_tuple");
        Function<Map<String, Object>, Object> consume =
                kwargs -> (int) argsFrom(kwargs)[0] + (int) argsFrom(kwargs)[1] + (int) kwargs.get("a_key");

        Object result = framework.transformIoByEvents(consume, "in", null, "result")
                .apply(new HashMap<>(Map.of("_args", new Object[]{0, 0})));

        assertEquals(6, result);
    }

    @Test
    @DisplayName("test_transform_io_generator_mode_expand")
    void testTransformIoGeneratorModeExpand() {
        assertEquals(List.of(1, 10, 2, 20, 3, 30),
                collect(expandEach(List.of((Object) 1, 2, 3).iterator())));
    }

    @Test
    @DisplayName("test_transform_io_generator_mode_filter")
    void testTransformIoGeneratorModeFilter() {
        assertEquals(List.of(1, 3, 5),
                collect(filterOdd(List.of((Object) 1, 2, 3, 4, 5).iterator())));
    }

    @Test
    @DisplayName("test_transform_io_generator_mode_stateful")
    void testTransformIoGeneratorModeStateful() {
        assertEquals(List.of(1, 3, 6, 10),
                collect(runningTotal(List.of((Object) 1, 2, 3, 4).iterator())));
    }

    @Test
    @DisplayName("test_transform_io_generator_mode_no_output_transform")
    void testTransformIoGeneratorModeNoOutputTransform() {
        assertEquals(List.of(0, 1, 2), collect(List.of((Object) 0, 1, 2).iterator()));
    }

    @Test
    @DisplayName("test_transform_io_generator_mode_sync_gen")
    void testTransformIoGeneratorModeSyncGen() {
        assertEquals(List.of(0, 2, 4), transformEach(List.of(0, 1, 2).iterator(), value -> (int) value * 2));
    }

    @Test
    @DisplayName("test_transform_io_generator_mode_invalid_for_non_gen")
    void testTransformIoGeneratorModeInvalidForNonGen() {
        Function<Map<String, Object>, Object> wrapped = framework.emitsStream("event", kwargs -> 42, "item");

        assertThrows(IllegalStateException.class, () -> wrapped.apply(new HashMap<>()));
    }

    @Test
    @DisplayName("test_transform_io_frame_mode_unchanged")
    void testTransformIoFrameModeUnchanged() {
        assertEquals(List.of(2, 4, 6), transformEach(List.of(1, 2, 3).iterator(), value -> (int) value * 2));
    }

    @Test
    @DisplayName("test_transform_io_events_generator_mode_expand")
    void testTransformIoEventsGeneratorModeExpand() {
        framework.onTransform("out_ev", kwargs -> expandEach((Iterator<Object>) kwargs.get("result")), "expand");

        Iterator<Object> transformed = (Iterator<Object>) framework.triggerTransform("out_ev",
                new HashMap<>(Map.of("result", List.of((Object) 1, 2).iterator())));

        assertEquals(List.of(1, 10, 2, 20), collect(transformed));
    }

    @Test
    @DisplayName("test_transform_io_events_generator_mode_filter")
    void testTransformIoEventsGeneratorModeFilter() {
        framework.onTransform("out_ev", kwargs -> filterOdd((Iterator<Object>) kwargs.get("result")), "keep_odd");

        Iterator<Object> transformed = (Iterator<Object>) framework.triggerTransform("out_ev",
                new HashMap<>(Map.of("result", List.of((Object) 1, 2, 3, 4, 5).iterator())));

        assertEquals(List.of(1, 3, 5), collect(transformed));
    }

    @Test
    @DisplayName("test_transform_io_events_generator_mode_no_callbacks")
    void testTransformIoEventsGeneratorModeNoCallbacks() {
        Iterator<Object> source = List.of((Object) 0, 1, 2).iterator();
        Object result = framework.triggerTransform("out_ev",
                new HashMap<>(Map.of("result", source)));

        assertSame(CallbackFramework.TRANSFORM_NOOP, result);
        assertEquals(List.of(0, 1, 2), collect(source));
    }

    @Test
    @DisplayName("test_transform_io_events_generator_mode_sync_gen")
    void testTransformIoEventsGeneratorModeSyncGen() {
        framework.onTransform("out_ev", kwargs -> transformEachIterator((Iterator<Object>) kwargs.get("result"),
                value -> (int) value * 2), "double");

        Iterator<Object> transformed = (Iterator<Object>) framework.triggerTransform("out_ev",
                new HashMap<>(Map.of("result", List.of((Object) 0, 1, 2).iterator())));

        assertEquals(List.of(0, 2, 4), collect(transformed));
    }

    private static List<Object> transformEach(Iterator<?> source, Function<Object, Object> transform) {
        List<Object> items = new ArrayList<>();
        while (source.hasNext()) {
            items.add(transform.apply(source.next()));
        }
        return items;
    }

    private static Iterator<Object> transformEachIterator(Iterator<Object> source, Function<Object, Object> transform) {
        return transformEach(source, transform).iterator();
    }

    private static Iterator<Object> expandEach(Iterator<Object> source) {
        List<Object> items = new ArrayList<>();
        while (source.hasNext()) {
            Object item = source.next();
            items.add(item);
            items.add((int) item * 10);
        }
        return items.iterator();
    }

    private static Iterator<Object> filterOdd(Iterator<Object> source) {
        List<Object> items = new ArrayList<>();
        while (source.hasNext()) {
            Object item = source.next();
            if ((int) item % 2 != 0) {
                items.add(item);
            }
        }
        return items.iterator();
    }

    private static Iterator<Object> runningTotal(Iterator<Object> source) {
        List<Object> items = new ArrayList<>();
        int total = 0;
        while (source.hasNext()) {
            total += (int) source.next();
            items.add(total);
        }
        return items.iterator();
    }
}
