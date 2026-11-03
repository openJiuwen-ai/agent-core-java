/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.runner.callback.test_framework_transform_io} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_transform_io.py}.</p>
 */
class FrameworkTransformIoPythonParityTest {

    private static final String GENERATOR_MODE = "generator";

    private static final List<String> PYTHON_TESTS = List.of(
            "test_transform_io_decorator_async_input_output",
            "test_transform_io_decorator_input_only",
            "test_transform_io_decorator_output_only",
            "test_transform_io_decorator_async_generator",
            "test_transform_io_decorator_sync_function",
            "test_transform_io_decorator_no_transform",
            "test_transform_io_by_events_input_output",
            "test_transform_io_by_events_input_only",
            "test_transform_io_by_events_output_only",
            "test_transform_io_by_events_no_callbacks_passthrough",
            "test_transform_io_by_events_last_callback_wins",
            "test_transform_io_by_events_async_generator",
            "test_transform_io_by_events_custom_result_key",
            "test_framework_transform_io_callable",
            "test_framework_transform_io_events",
            "test_framework_transform_io_events_prefer_over_callable",
            "test_framework_transform_io_sync_generator_with_events",
            "test_transform_io_decorator_sync_generator_direct",
            "test_transform_io_decorator_async_input_transform",
            "test_transform_io_by_events_input_tuple_return",
            "test_transform_io_generator_mode_expand",
            "test_transform_io_generator_mode_filter",
            "test_transform_io_generator_mode_stateful",
            "test_transform_io_generator_mode_no_output_transform",
            "test_transform_io_generator_mode_sync_gen",
            "test_transform_io_generator_mode_invalid_for_non_gen",
            "test_transform_io_frame_mode_unchanged",
            "test_transform_io_events_generator_mode_expand",
            "test_transform_io_events_generator_mode_filter",
            "test_transform_io_events_generator_mode_no_callbacks",
            "test_transform_io_events_generator_mode_sync_gen"
    );

    @TestFactory
    Collection<DynamicTest> pythonFrameworkTransformIoCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        switch (name) {
            case "test_transform_io_decorator_async_input_output" -> transformIoDecoratorAsyncInputOutput();
            case "test_transform_io_decorator_input_only" -> transformIoDecoratorInputOnly();
            case "test_transform_io_decorator_output_only" -> transformIoDecoratorOutputOnly();
            case "test_transform_io_decorator_async_generator" -> transformIoDecoratorAsyncGenerator();
            case "test_transform_io_decorator_sync_function" -> transformIoDecoratorSyncFunction();
            case "test_transform_io_decorator_no_transform" -> transformIoDecoratorNoTransform();
            case "test_transform_io_by_events_input_output" -> transformIoByEventsInputOutput();
            case "test_transform_io_by_events_input_only" -> transformIoByEventsInputOnly();
            case "test_transform_io_by_events_output_only" -> transformIoByEventsOutputOnly();
            case "test_transform_io_by_events_no_callbacks_passthrough" -> transformIoByEventsNoCallbacksPassthrough();
            case "test_transform_io_by_events_last_callback_wins" -> transformIoByEventsLastCallbackWins();
            case "test_transform_io_by_events_async_generator" -> transformIoByEventsAsyncGenerator();
            case "test_transform_io_by_events_custom_result_key" -> transformIoByEventsCustomResultKey();
            case "test_framework_transform_io_callable" -> frameworkTransformIoCallable();
            case "test_framework_transform_io_events" -> frameworkTransformIoEvents();
            case "test_framework_transform_io_events_prefer_over_callable" -> frameworkTransformIoEventsPreferOverCallable();
            case "test_framework_transform_io_sync_generator_with_events" -> frameworkTransformIoSyncGeneratorWithEvents();
            case "test_transform_io_decorator_sync_generator_direct" -> transformIoDecoratorSyncGeneratorDirect();
            case "test_transform_io_decorator_async_input_transform" -> transformIoDecoratorAsyncInputTransform();
            case "test_transform_io_by_events_input_tuple_return" -> transformIoByEventsInputTupleReturn();
            case "test_transform_io_generator_mode_expand" -> transformIoGeneratorModeExpand();
            case "test_transform_io_generator_mode_filter" -> transformIoGeneratorModeFilter();
            case "test_transform_io_generator_mode_stateful" -> transformIoGeneratorModeStateful();
            case "test_transform_io_generator_mode_no_output_transform" -> transformIoGeneratorModeNoOutputTransform();
            case "test_transform_io_generator_mode_sync_gen" -> transformIoGeneratorModeSyncGen();
            case "test_transform_io_generator_mode_invalid_for_non_gen" -> transformIoGeneratorModeInvalidForNonGen();
            case "test_transform_io_frame_mode_unchanged" -> transformIoFrameModeUnchanged();
            case "test_transform_io_events_generator_mode_expand" -> transformIoEventsGeneratorModeExpand();
            case "test_transform_io_events_generator_mode_filter" -> transformIoEventsGeneratorModeFilter();
            case "test_transform_io_events_generator_mode_no_callbacks" -> transformIoEventsGeneratorModeNoCallbacks();
            case "test_transform_io_events_generator_mode_sync_gen" -> transformIoEventsGeneratorModeSyncGen();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void transformIoDecoratorAsyncInputOutput() {
        Function<Map<String, Object>, Map<String, Object>> addOne = kwargs -> {
            Map<String, Object> changed = mutableCopy(kwargs);
            changed.put("n", asInt(changed.getOrDefault("n", 0)) + 1);
            return changed;
        };
        Function<Map<String, Object>, Object> compute = CallbackDecorators.createTransformIoDecorator(
                addOne, value -> asInt(value) * 2
        ).apply(kwargs -> kwargs.get("n"));

        assertEquals(2, compute.apply(mapOf("n", 0)));
    }

    private void transformIoDecoratorInputOnly() {
        Function<Map<String, Object>, Object> fetch = CallbackDecorators.createTransformIoDecorator(
                kwargs -> mapOf("limit", 10), null
        ).apply(kwargs -> kwargs.get("limit"));

        assertEquals(10, fetch.apply(mapOf("limit", 5)));
    }

    private void transformIoDecoratorOutputOnly() {
        Function<Map<String, Object>, Object> getValue = CallbackDecorators.createTransformIoDecorator(
                null, String::valueOf
        ).apply(kwargs -> 42);

        assertEquals("42", getValue.apply(Map.of()));
    }

    private void transformIoDecoratorAsyncGenerator() {
        Function<Map<String, Object>, Object> stream = CallbackDecorators.createTransformIoDecorator(
                null, value -> asInt(value) * 2
        ).apply(kwargs -> iteratorOf(1, 2, 3));

        assertEquals(List.of(2, 4, 6), toList(stream.apply(Map.of())));
    }

    private void transformIoDecoratorSyncFunction() {
        Function<Map<String, Object>, Object> syncCompute = CallbackDecorators.createTransformIoDecorator(
                kwargs -> mapOf("n", 3), value -> asInt(value) * 3
        ).apply(kwargs -> kwargs.getOrDefault("n", 0));

        assertEquals(9, syncCompute.apply(Map.of()));
    }

    private void transformIoDecoratorNoTransform() {
        Function<Map<String, Object>, Object> identity = CallbackDecorators.createTransformIoDecorator(
                null, null
        ).apply(kwargs -> kwargs.get("x"));

        assertEquals(7, identity.apply(mapOf("x", 7)));
    }

    private void transformIoByEventsInputOutput() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "input_transform", "normalize_input", kwargs -> {
            Map<String, Object> changed = mutableCopy(kwargs);
            changed.putIfAbsent("limit", 10);
            return new CallbackDecorators.BoundArgs(args(kwargs), changed);
        });
        registerTransform(framework, "output_transform", "serialize_output",
                kwargs -> "result:" + pythonCountDict(asInt(((Map<?, ?>) kwargs.get("result")).get("count"))));
        Function<Map<String, Object>, Object> fetchData = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, "input_transform", "output_transform", "result"
        ).apply(kwargs -> mapOf("count", kwargs.get("limit")));

        assertEquals("result:{'count': 10}", fetchData.apply(kwargsWithArgs(5)));
    }

    private void transformIoByEventsInputOnly() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "input_event", "add_one", kwargs -> {
            Map<String, Object> changed = mutableCopy(kwargs);
            changed.put("n", asInt(changed.getOrDefault("n", 0)) + 1);
            return new CallbackDecorators.BoundArgs(args(kwargs), changed);
        });
        Function<Map<String, Object>, Object> compute = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, "input_event", null, "result"
        ).apply(kwargs -> kwargs.get("n"));

        assertEquals(1, compute.apply(kwargsWithArgs(0)));
    }

    private void transformIoByEventsOutputOnly() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "output_event", "double", kwargs -> asInt(kwargs.get("result")) * 2);
        Function<Map<String, Object>, Object> getValue = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, null, "output_event", "result"
        ).apply(kwargs -> 21);

        assertEquals(42, getValue.apply(Map.of()));
    }

    private void transformIoByEventsNoCallbacksPassthrough() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> identity = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, "nonexistent_input", "nonexistent_output", "result"
        ).apply(kwargs -> firstArg(kwargs));

        assertEquals(100, identity.apply(kwargsWithArgs(100)));
    }

    private void transformIoByEventsLastCallbackWins() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "out", "first", 0, kwargs -> asInt(kwargs.get("result")) + 1);
        registerTransform(framework, "out", "second", 10, kwargs -> asInt(kwargs.get("result")) * 2);
        Function<Map<String, Object>, Object> get = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, null, "out", "result"
        ).apply(kwargs -> 5);

        assertEquals(6, get.apply(Map.of()));
    }

    private void transformIoByEventsAsyncGenerator() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "item", "double", kwargs -> asInt(kwargs.get("result")) * 2);
        Function<Map<String, Object>, Object> stream = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, null, "item", "result"
        ).apply(kwargs -> iteratorOf(1, 2));

        assertEquals(List.of(2, 4), toList(stream.apply(Map.of())));
    }

    private void transformIoByEventsCustomResultKey() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "custom_out", "transform", kwargs -> asInt(kwargs.get("value")) * 3);
        Function<Map<String, Object>, Object> get = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, null, "custom_out", "value"
        ).apply(kwargs -> 7);

        assertEquals(21, get.apply(Map.of()));
    }

    private void frameworkTransformIoCallable() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> fetch = framework.transformIo(
                kwargs -> mapOf("limit", 10), String::valueOf
        ).apply(kwargs -> kwargs.get("limit"));

        assertEquals("10", fetch.apply(mapOf("limit", 5)));
    }

    private void frameworkTransformIoEvents() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "in_ev", "in_cb", kwargs -> {
            int base = args(kwargs).length > 0 ? asInt(args(kwargs)[0]) : asInt(kwargs.getOrDefault("x", 0));
            Map<String, Object> changed = mutableCopy(kwargs);
            changed.put("x", base + 1);
            return new CallbackDecorators.BoundArgs(args(kwargs), changed);
        });
        registerTransform(framework, "out_ev", "out_cb", kwargs -> asInt(kwargs.get("result")) + 100);
        Function<Map<String, Object>, Object> compute = framework.transformIoByEvents(
                "in_ev", "out_ev", "result"
        ).apply(kwargs -> kwargs.get("x"));

        assertEquals(102, compute.apply(kwargsWithArgs(1)));
    }

    private void frameworkTransformIoEventsPreferOverCallable() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "ev", "event_cb", kwargs -> "from_event");
        Function<Map<String, Object>, Object> get = framework.transformIo(
                null, "ev", "result", null, ignored -> "from_callable", "frame"
        ).apply(kwargs -> "raw");

        assertEquals("from_event", get.apply(Map.of()));
    }

    private void frameworkTransformIoSyncGeneratorWithEvents() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "item", "double", kwargs -> asInt(kwargs.get("result")) * 2);
        Function<Map<String, Object>, Object> syncStream = framework.transformIoByEvents(
                null, "item", "result"
        ).apply(kwargs -> iteratorOf(10, 20));

        assertEquals(List.of(20, 40), toList(syncStream.apply(Map.of())));
    }

    private void transformIoDecoratorSyncGeneratorDirect() {
        Function<Map<String, Object>, Object> syncGen = CallbackDecorators.createTransformIoDecorator(
                null, value -> -asInt(value)
        ).apply(kwargs -> iteratorOf(1, 2));

        assertEquals(List.of(-1, -2), toList(syncGen.apply(Map.of())));
    }

    private void transformIoDecoratorAsyncInputTransform() {
        Function<Map<String, Object>, Object> compute = CallbackDecorators.createTransformIoDecorator(
                kwargs -> CompletableFuture.completedFuture(mapOf("n", asInt(kwargs.getOrDefault("n", 0)) + 1)),
                null
        ).apply(kwargs -> kwargs.get("n"));

        assertEquals(1, compute.apply(mapOf("n", 0)));
    }

    private void transformIoByEventsInputTupleReturn() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "in", "return_tuple",
                kwargs -> new CallbackDecorators.BoundArgs(new Object[]{1, 2}, mapOf("a_key", 3)));
        Function<Map<String, Object>, Object> consume = CallbackDecorators.createTransformIoByEventsDecorator(
                framework, "in", null, "result"
        ).apply(kwargs -> asInt(args(kwargs)[0]) + asInt(args(kwargs)[1]) + asInt(kwargs.get("a_key")));

        assertEquals(6, consume.apply(kwargsWithArgs(0, 0)));
    }

    private void transformIoGeneratorModeExpand() {
        Function<Map<String, Object>, Object> gen = CallbackDecorators.createTransformIoDecorator(
                null, source -> expandByTen(source), GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(1, 2, 3));

        assertEquals(List.of(1, 10, 2, 20, 3, 30), toList(gen.apply(Map.of())));
    }

    private void transformIoGeneratorModeFilter() {
        Function<Map<String, Object>, Object> gen = CallbackDecorators.createTransformIoDecorator(
                null, FrameworkTransformIoPythonParityTest::keepOdd, GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(1, 2, 3, 4, 5));

        assertEquals(List.of(1, 3, 5), toList(gen.apply(Map.of())));
    }

    private void transformIoGeneratorModeStateful() {
        Function<Map<String, Object>, Object> gen = CallbackDecorators.createTransformIoDecorator(
                null, FrameworkTransformIoPythonParityTest::runningTotal, GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(1, 2, 3, 4));

        assertEquals(List.of(1, 3, 6, 10), toList(gen.apply(Map.of())));
    }

    private void transformIoGeneratorModeNoOutputTransform() {
        Function<Map<String, Object>, Object> gen = CallbackDecorators.createTransformIoDecorator(
                null, null, GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(0, 1, 2));

        assertEquals(List.of(0, 1, 2), toList(gen.apply(Map.of())));
    }

    private void transformIoGeneratorModeSyncGen() {
        Function<Map<String, Object>, Object> syncGen = CallbackDecorators.createTransformIoDecorator(
                null, FrameworkTransformIoPythonParityTest::doubleSource, GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(0, 1, 2));

        assertEquals(List.of(0, 2, 4), toList(syncGen.apply(Map.of())));
    }

    private void transformIoGeneratorModeInvalidForNonGen() {
        Function<Map<String, Object>, Object> notGen = CallbackDecorators.createTransformIoDecorator(
                null, null, GENERATOR_MODE
        ).apply(kwargs -> 42);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> notGen.apply(Map.of()));
        assertTrue(error.getMessage().contains("output_mode='generator'"));
    }

    private void transformIoFrameModeUnchanged() {
        Function<Map<String, Object>, Object> gen = CallbackDecorators.createTransformIoDecorator(
                null, value -> asInt(value) * 2, "frame"
        ).apply(kwargs -> iteratorOf(1, 2, 3));

        assertEquals(List.of(2, 4, 6), toList(gen.apply(Map.of())));
    }

    private void transformIoEventsGeneratorModeExpand() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "out_ev", "expand", kwargs -> expandByTen(kwargs.get("result")));
        Function<Map<String, Object>, Object> gen = framework.transformIoByEvents(
                null, "out_ev", "result", GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(1, 2));

        assertEquals(List.of(1, 10, 2, 20), toList(gen.apply(Map.of())));
    }

    private void transformIoEventsGeneratorModeFilter() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "out_ev", "keep_odd", kwargs -> keepOdd(kwargs.get("result")));
        Function<Map<String, Object>, Object> gen = framework.transformIoByEvents(
                null, "out_ev", "result", GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(1, 2, 3, 4, 5));

        assertEquals(List.of(1, 3, 5), toList(gen.apply(Map.of())));
    }

    private void transformIoEventsGeneratorModeNoCallbacks() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> gen = framework.transformIoByEvents(
                null, "out_ev", "result", GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(0, 1, 2));

        assertEquals(List.of(0, 1, 2), toList(gen.apply(Map.of())));
    }

    private void transformIoEventsGeneratorModeSyncGen() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "out_ev", "double", kwargs -> doubleSource(kwargs.get("result")));
        Function<Map<String, Object>, Object> syncGen = framework.transformIoByEvents(
                null, "out_ev", "result", GENERATOR_MODE
        ).apply(kwargs -> iteratorOf(0, 1, 2));

        assertEquals(List.of(0, 2, 4), toList(syncGen.apply(Map.of())));
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static void registerTransform(
            AsyncCallbackFramework framework,
            String event,
            String name,
            Function<Map<String, Object>, Object> callback
    ) {
        registerTransform(framework, event, name, 0, callback);
    }

    private static void registerTransform(
            AsyncCallbackFramework framework,
            String event,
            String name,
            int priority,
            Function<Map<String, Object>, Object> callback
    ) {
        framework.registerSync(event, named(name, callback), priority, false, "default", Set.of(), List.of(),
                null, null, 0, 0.0, null, AsyncCallbackFramework.CALLBACK_TYPE_TRANSFORM);
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private static Object firstArg(Map<String, Object> kwargs) {
        return args(kwargs)[0];
    }

    private static Object[] args(Map<String, Object> kwargs) {
        Object value = kwargs.get("_args");
        return value instanceof Object[] values ? values : new Object[0];
    }

    private static int asInt(Object value) {
        return ((Number) value).intValue();
    }

    private static Map<String, Object> kwargsWithArgs(Object... args) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("_args", args.clone());
        return kwargs;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private static Map<String, Object> mutableCopy(Map<String, Object> kwargs) {
        Map<String, Object> copy = new LinkedHashMap<>(kwargs);
        copy.remove("_args");
        return copy;
    }

    private static Iterator<Integer> iteratorOf(Integer... values) {
        return List.of(values).iterator();
    }

    private static Iterator<Integer> expandByTen(Object source) {
        List<Integer> expanded = new ArrayList<>();
        Iterator<?> iterator = iterator(source);
        while (iterator.hasNext()) {
            int item = asInt(iterator.next());
            expanded.add(item);
            expanded.add(item * 10);
        }
        return expanded.iterator();
    }

    private static Iterator<Integer> keepOdd(Object source) {
        List<Integer> kept = new ArrayList<>();
        Iterator<?> iterator = iterator(source);
        while (iterator.hasNext()) {
            int item = asInt(iterator.next());
            if (item % 2 != 0) {
                kept.add(item);
            }
        }
        return kept.iterator();
    }

    private static Iterator<Integer> runningTotal(Object source) {
        List<Integer> totals = new ArrayList<>();
        Iterator<?> iterator = iterator(source);
        int total = 0;
        while (iterator.hasNext()) {
            total += asInt(iterator.next());
            totals.add(total);
        }
        return totals.iterator();
    }

    private static Iterator<Integer> doubleSource(Object source) {
        List<Integer> doubled = new ArrayList<>();
        Iterator<?> iterator = iterator(source);
        while (iterator.hasNext()) {
            doubled.add(asInt(iterator.next()) * 2);
        }
        return doubled.iterator();
    }

    private static Iterator<?> iterator(Object source) {
        if (source instanceof Iterator<?> iterator) {
            return iterator;
        }
        if (source instanceof Iterable<?> iterable) {
            return iterable.iterator();
        }
        throw new IllegalArgumentException("Expected iterator source");
    }

    private static List<Object> toList(Object value) {
        Iterator<?> iterator = iterator(value);
        List<Object> items = new ArrayList<>();
        while (iterator.hasNext()) {
            items.add(iterator.next());
        }
        return items;
    }

    private static String pythonCountDict(int count) {
        return "{'count': " + count + "}";
    }

    private record NamedCallback(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            return delegate.apply(kwargs);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
