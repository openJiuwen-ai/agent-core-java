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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Callback framework emit sync test cases.
 *
 * <p>Mirrors Python's {@code test_emit_sync.py} in
 * {@code tests/unit_tests/core/runner/callback/test_emit_sync}.</p>
 */
@DisplayName("Emit Sync Tests")
class TestEmitSync {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(true, false);
    }

    private static Object[] argsFrom(Map<String, Object> kwargs) {
        Object raw = kwargs.get("_args");
        return raw instanceof Object[] args ? args : new Object[0];
    }

    private static List<Object> collect(Object rawIterator) {
        Iterator<?> iterator = (Iterator<?>) rawIterator;
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    @Test
    @DisplayName("test_emit_before_sync_function - sync function triggers before event")
    void testEmitBeforeSyncFunction() {
        List<Object> triggered = new ArrayList<>();
        framework.on("before_sync", kwargs -> {
            triggered.add(argsFrom(kwargs)[0]);
            return null;
        }, "handler");

        Function<Map<String, Object>, Object> compute = kwargs -> (int) argsFrom(kwargs)[0] * 2;
        Function<Map<String, Object>, Object> wrapped = framework.triggerOnCall("before_sync", compute, false, true);

        Object result = wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{5})));

        assertEquals(10, result);
        assertEquals(List.of(5), triggered);
    }

    @Test
    @DisplayName("test_emit_before_sync_generator - generator triggers before event once")
    void testEmitBeforeSyncGenerator() {
        List<String> triggered = new ArrayList<>();
        framework.on("before_gen", kwargs -> {
            triggered.add("fired");
            return null;
        }, "handler");

        Function<Map<String, Object>, Object> generate = kwargs -> List.of(1, 2, 3).iterator();
        Function<Map<String, Object>, Object> wrapped = framework.triggerOnCall("before_gen", generate, false, false);

        assertEquals(List.of(1, 2, 3), collect(wrapped.apply(new HashMap<>())));
        assertEquals(List.of("fired"), triggered);
    }

    @Test
    @DisplayName("test_emit_after_sync_function - sync function emits result after execution")
    void testEmitAfterSyncFunction() {
        List<Object> receivedResults = new ArrayList<>();
        framework.on("after_sync", kwargs -> {
            receivedResults.add(kwargs.get("result"));
            return null;
        }, "handler");

        Function<Map<String, Object>, Object> compute = kwargs -> (int) argsFrom(kwargs)[0] + 1;
        Function<Map<String, Object>, Object> wrapped = framework.emits("after_sync", compute, "result", false);

        Object result = wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{10})));

        assertEquals(11, result);
        assertEquals(List.of(11), receivedResults);
    }

    @Test
    @DisplayName("test_emit_after_sync_generator_per_item - stream emits every item")
    void testEmitAfterSyncGeneratorPerItem() {
        List<Object> receivedItems = new ArrayList<>();
        framework.on("after_item", kwargs -> {
            receivedItems.add(kwargs.get("item"));
            return null;
        }, "handler");

        Function<Map<String, Object>, Object> generate = kwargs -> List.of("a", "b").iterator();
        Function<Map<String, Object>, Object> wrapped = framework.emitsStream("after_item", generate, "item");

        assertEquals(List.of("a", "b"), collect(wrapped.apply(new HashMap<>())));
        assertEquals(List.of("a", "b"), receivedItems);
    }

    @Test
    @DisplayName("test_emit_after_sync_generator_once - stream can emit collected result once")
    void testEmitAfterSyncGeneratorOnce() {
        List<Object> received = new ArrayList<>();
        framework.on("after_all", kwargs -> {
            received.add(kwargs.get("result"));
            return null;
        }, "handler");

        Function<Map<String, Object>, Object> generate = kwargs -> List.of(10, 20);
        Function<Map<String, Object>, Object> wrapped = framework.emits("after_all", generate, "result", false);

        Object result = wrapped.apply(new HashMap<>());

        assertEquals(List.of(10, 20), result);
        assertEquals(List.of(List.of(10, 20)), received);
    }

    @Test
    @DisplayName("test_emit_after_sync_generator_per_item_pass_args - stream event includes original args")
    void testEmitAfterSyncGeneratorPerItemPassArgs() {
        List<Map<String, Object>> received = new ArrayList<>();
        framework.on("after_item_args", kwargs -> {
            received.add(new HashMap<>(kwargs));
            return null;
        }, "handler");

        Function<Map<String, Object>, Object> generate = kwargs -> List.of("val:1", "val:2").iterator();
        Function<Map<String, Object>, Object> wrapped =
                framework.emitsStream("after_item_args", generate, "item", true);

        Map<String, Object> call = new HashMap<>();
        call.put("_args", new Object[]{"val"});
        call.put("sep", ":");
        assertEquals(List.of("val:1", "val:2"), collect(wrapped.apply(call)));

        assertEquals(2, received.size());
        assertEquals(":", received.get(0).get("sep"));
        assertEquals("val:1", received.get(0).get("item"));
        assertEquals("val:2", received.get(1).get("item"));
        assertEquals("val", ((Object[]) received.get(0).get("_args"))[0]);
    }

    @Test
    @DisplayName("test_emit_after_gen_per_item_pass_args - each yielded item sees source argument")
    void testEmitAfterGenPerItemPassArgs() {
        List<Object> receivedArgs = new ArrayList<>();
        List<Object> receivedResult = new ArrayList<>();
        framework.on("end", kwargs -> {
            receivedArgs.add(argsFrom(kwargs)[0]);
            receivedResult.add(kwargs.get("item"));
            return null;
        }, "on_end");

        Function<Map<String, Object>, Object> generate = kwargs -> {
            int num = (int) argsFrom(kwargs)[0];
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < num; i++) {
                items.add(i * 2);
            }
            return items.iterator();
        };
        Function<Map<String, Object>, Object> wrapped = framework.emitsStream("end", generate, "item", true);

        collect(wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{3}))));

        assertEquals(List.of(3, 3, 3), receivedArgs);
        assertEquals(List.of(0, 2, 4), receivedResult);
    }

    @Test
    @DisplayName("test_emit_around_sync_function - before and after events wrap sync function")
    void testEmitAroundSyncFunction() {
        List<List<Object>> events = new ArrayList<>();
        framework.on("start", kwargs -> {
            events.add(List.of("start", argsFrom(kwargs)[0]));
            return null;
        }, "on_start");
        framework.on("end", kwargs -> {
            events.add(List.of("end", kwargs.get("result")));
            return null;
        }, "on_end");

        Function<Map<String, Object>, Object> compute = kwargs -> (int) argsFrom(kwargs)[0] * 3;
        Function<Map<String, Object>, Object> wrapped =
                framework.emitAround("start", "end", compute, true, true, null);

        Object result = wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{4})));

        assertEquals(12, result);
        assertEquals(List.of("start", 4), events.get(0));
        assertEquals(List.of("end", 12), events.get(1));
    }

    @Test
    @DisplayName("test_emit_around_sync_function_error - error event fires and original exception propagates")
    void testEmitAroundSyncFunctionError() {
        List<String> errors = new ArrayList<>();
        framework.on("err", kwargs -> {
            errors.add(((Exception) kwargs.get("error")).getMessage());
            return null;
        }, "on_error");

        Function<Map<String, Object>, Object> failing = kwargs -> {
            throw new IllegalArgumentException("boom");
        };
        Function<Map<String, Object>, Object> wrapped =
                framework.emitAround("s", "e", failing, false, true, "err");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> wrapped.apply(new HashMap<>()));

        assertEquals("boom", thrown.getMessage());
        assertEquals(List.of("boom"), errors);
    }

    @Test
    @DisplayName("test_emit_around_sync_generator - generator result is emitted after completion")
    void testEmitAroundSyncGenerator() {
        List<Object> events = new ArrayList<>();
        framework.on("gen_start", kwargs -> {
            events.add("start");
            return null;
        }, "on_start");
        framework.on("gen_end", kwargs -> {
            events.add(List.of("end", kwargs.get("result")));
            return null;
        }, "on_end");

        Function<Map<String, Object>, Object> generate = kwargs -> List.of("x", "y");
        Function<Map<String, Object>, Object> wrapped =
                framework.emitAround("gen_start", "gen_end", generate, false, true, null);

        Object result = wrapped.apply(new HashMap<>());

        assertEquals(List.of("x", "y"), result);
        assertEquals("start", events.get(0));
        assertEquals(List.of("end", List.of("x", "y")), events.get(1));
        assertTrue(events.size() == 2);
    }
}
