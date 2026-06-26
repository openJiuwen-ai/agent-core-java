/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's transform IO event pipeline tests in
 * {@code tests/unit_tests/core/runner/callback/test_transform_io.py}.
 */
class TransformIoEventPipelineMissingTest {

    @Test
    void triggerTransformReturnsNoopWhenNoCallbacks() {
        AsyncCallbackFramework framework = framework();

        Object result = framework.triggerTransform("some_event", new Object[]{"arg1"}, Map.of());

        assertSame(CallbackDecorators.TRANSFORM_NOOP, result);
    }

    @Test
    void triggerTransformIgnoresRegularCallbacks() {
        AsyncCallbackFramework framework = framework();
        List<Object> called = new ArrayList<>();
        registerRegular(framework, "my_event", "regular_handler", kwargs -> {
            called.add(arg(kwargs, 0));
            return "regular";
        });

        Object result = framework.triggerTransform("my_event", new Object[]{42}, Map.of());

        assertSame(CallbackDecorators.TRANSFORM_NOOP, result);
        assertEquals(List.of(), called);
    }

    @Test
    void triggerTransformRunsTransformCallbacks() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "my_event", "transform_handler", kwargs -> asInt(arg(kwargs, 0)) * 2);

        Object result = framework.triggerTransform("my_event", new Object[]{5}, Map.of());

        assertEquals(10, result);
    }

    @Test
    void triggerTransformReturnsLastResult() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "ev", "h1", 10, kwargs -> asInt(arg(kwargs, 0)) + 1);
        registerTransform(framework, "ev", "h2", 0, kwargs -> asInt(arg(kwargs, 0)) * 100);

        Object result = framework.triggerTransform("ev", new Object[]{3}, Map.of());

        assertEquals(300, result);
    }

    @Test
    void triggerTransformCoexistsWithRegularTrigger() {
        AsyncCallbackFramework framework = framework();
        List<Object> regularCalled = new ArrayList<>();
        List<Object> transformCalled = new ArrayList<>();
        registerRegular(framework, "ev", "regular", kwargs -> {
            regularCalled.add(arg(kwargs, 0));
            return null;
        });
        registerTransform(framework, "ev", "transform", kwargs -> {
            transformCalled.add(arg(kwargs, 0));
            return arg(kwargs, 0);
        });

        framework.triggerResults("ev", new Object[]{7}, Map.of());
        Object result = framework.triggerTransform("ev", new Object[]{9}, Map.of());

        assertEquals(9, result);
        assertEquals(List.of(7), regularCalled);
        assertEquals(List.of(9), transformCalled);
    }

    @Test
    void onTransformRegistersTransformType() {
        AsyncCallbackFramework framework = framework();
        framework.onTransform("ev", 0).apply(named("handler", kwargs -> asInt(arg(kwargs, 0)) + 10));

        Object result = framework.triggerTransform("ev", new Object[]{5}, Map.of());
        List<Object> triggerResults = framework.triggerResults("ev", new Object[]{5}, Map.of());

        assertEquals(15, result);
        assertEquals(List.of(), triggerResults);
    }

    @Test
    void transformIoIdentityWhenNoTransformCallbacks() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> add = framework.transformIoByEvents("in_ev", "out_ev", "result")
                .apply(kwargs -> asInt(arg(kwargs, 0)) + asInt(arg(kwargs, 1)));

        assertEquals(5, add.apply(kwargsWithArgs(2, 3)));
    }

    @Test
    void transformIoStreamIdentityWhenNoTransformCallbacks() {
        AsyncCallbackFramework framework = framework();
        Function<Map<String, Object>, Object> generator = framework.transformIoByEvents("in_ev", "out_ev", "result")
                .apply(kwargs -> range(asInt(arg(kwargs, 0))));

        assertEquals(List.of(0, 1, 2), toList(generator.apply(kwargsWithArgs(3))));
    }

    @Test
    void transformIoModifiesInput() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "in_ev", "double_first", kwargs ->
                new CallbackDecorators.BoundArgs(
                        new Object[]{asInt(arg(kwargs, 0)) * 2, arg(kwargs, 1)},
                        Map.of()
                ));
        Function<Map<String, Object>, Object> add = framework.transformIoByEvents("in_ev", "out_ev", "result")
                .apply(kwargs -> asInt(arg(kwargs, 0)) + asInt(arg(kwargs, 1)));

        assertEquals(10, add.apply(kwargsWithArgs(3, 4)));
    }

    @Test
    void transformIoModifiesOutput() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "out_ev", "negate", kwargs -> -asInt(kwargs.get("result")));
        Function<Map<String, Object>, Object> add = framework.transformIoByEvents("in_ev", "out_ev", "result")
                .apply(kwargs -> asInt(arg(kwargs, 0)) + asInt(arg(kwargs, 1)));

        assertEquals(-5, add.apply(kwargsWithArgs(2, 3)));
    }

    @Test
    void transformIoModifiesBothInputAndOutput() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "in_ev", "increment_a", kwargs ->
                new CallbackDecorators.BoundArgs(
                        new Object[]{asInt(arg(kwargs, 0)) + 1, arg(kwargs, 1)},
                        Map.of()
                ));
        registerTransform(framework, "out_ev", "double_result", kwargs -> asInt(kwargs.get("result")) * 2);
        Function<Map<String, Object>, Object> add = framework.transformIoByEvents("in_ev", "out_ev", "result")
                .apply(kwargs -> asInt(arg(kwargs, 0)) + asInt(arg(kwargs, 1)));

        assertEquals(8, add.apply(kwargsWithArgs(1, 2)));
    }

    @Test
    void transformIoStreamOutputFiresPerItem() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "out_ev", "square", kwargs -> {
            int value = asInt(kwargs.get("result"));
            return value * value;
        });
        Function<Map<String, Object>, Object> generator = framework.transformIoByEvents("in_ev", "out_ev", "result")
                .apply(kwargs -> rangeClosed(1, asInt(arg(kwargs, 0))));

        assertEquals(List.of(1, 4, 9, 16), toList(generator.apply(kwargsWithArgs(4))));
    }

    @Test
    void transformIoStreamInputModifiesArg() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "in_ev", "double_n", kwargs ->
                new CallbackDecorators.BoundArgs(new Object[]{asInt(arg(kwargs, 0)) * 2}, Map.of()));
        Function<Map<String, Object>, Object> generator = framework.transformIoByEvents("in_ev", "out_ev", "result")
                .apply(kwargs -> range(asInt(arg(kwargs, 0))));

        assertEquals(List.of(0, 1, 2, 3, 4, 5), toList(generator.apply(kwargsWithArgs(3))));
    }

    @Test
    void disabledTransformCallbackIsSkipped() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "ev", "handler", kwargs -> asInt(arg(kwargs, 0)) * 99);
        framework.getCallbacks().get("ev").forEach(callbackInfo -> callbackInfo.setEnabled(false));

        Object result = framework.triggerTransform("ev", new Object[]{5}, Map.of());

        assertSame(CallbackDecorators.TRANSFORM_NOOP, result);
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static void registerRegular(
            AsyncCallbackFramework framework,
            String event,
            String name,
            Function<Map<String, Object>, Object> callback
    ) {
        framework.registerSync(event, named(name, callback), 0, false, "default", Set.of(), List.of(),
                null, null, 0, 0.0, null, "");
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

    private static Object arg(Map<String, Object> kwargs, int index) {
        Object value = kwargs.get("_args");
        Object[] args = value instanceof Object[] values ? values : new Object[0];
        return args[index];
    }

    private static int asInt(Object value) {
        return ((Number) value).intValue();
    }

    private static Map<String, Object> kwargsWithArgs(Object... values) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("_args", values.clone());
        return kwargs;
    }

    private static Iterator<Integer> range(int exclusiveEnd) {
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < exclusiveEnd; index++) {
            values.add(index);
        }
        return values.iterator();
    }

    private static Iterator<Integer> rangeClosed(int start, int inclusiveEnd) {
        List<Integer> values = new ArrayList<>();
        for (int value = start; value <= inclusiveEnd; value++) {
            values.add(value);
        }
        return values.iterator();
    }

    private static List<Object> toList(Object value) {
        Iterator<?> iterator = value instanceof Iterator<?> typedIterator ? typedIterator : ((Iterable<?>) value).iterator();
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
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
