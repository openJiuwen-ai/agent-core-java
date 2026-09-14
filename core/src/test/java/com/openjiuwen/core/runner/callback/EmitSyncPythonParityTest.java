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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code test_emit_sync.py} in
 * {@code tests/unit_tests/core/runner/callback/test_emit_sync.py}.</p>
 */
class EmitSyncPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_emit_before_sync_function",
            "test_emit_before_sync_generator",
            "test_emit_after_sync_function",
            "test_emit_after_sync_generator_per_item",
            "test_emit_after_sync_generator_once",
            "test_emit_after_sync_generator_per_item_pass_args",
            "test_emit_after_gen_per_item_pass_args",
            "test_emit_around_sync_function",
            "test_emit_around_sync_function_error",
            "test_emit_around_sync_generator"
    );

    @TestFactory
    Collection<DynamicTest> pythonEmitSyncCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        switch (name) {
            case "test_emit_before_sync_function" -> emitBeforeSyncFunction();
            case "test_emit_before_sync_generator" -> emitBeforeSyncGenerator();
            case "test_emit_after_sync_function" -> emitAfterSyncFunction();
            case "test_emit_after_sync_generator_per_item" -> emitAfterSyncGeneratorPerItem();
            case "test_emit_after_sync_generator_once" -> emitAfterSyncGeneratorOnce();
            case "test_emit_after_sync_generator_per_item_pass_args" -> emitAfterSyncGeneratorPerItemPassArgs();
            case "test_emit_after_gen_per_item_pass_args" -> emitAfterGenPerItemPassArgs();
            case "test_emit_around_sync_function" -> emitAroundSyncFunction();
            case "test_emit_around_sync_function_error" -> emitAroundSyncFunctionError();
            case "test_emit_around_sync_generator" -> emitAroundSyncGenerator();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void emitBeforeSyncFunction() {
        AsyncCallbackFramework framework = framework();
        List<Integer> triggered = new ArrayList<>();
        framework.on("before_sync").apply(kwargs -> {
            triggered.add((Integer) args(kwargs)[0]);
            return null;
        });
        Function<Map<String, Object>, Object> compute = framework.emitBefore("before_sync", true, Map.of())
                .apply(kwargs -> ((Integer) args(kwargs)[0]) * 2);

        Object result = compute.apply(kwargsWithArgs(5));

        assertEquals(10, result);
        assertEquals(List.of(5), triggered);
    }

    private void emitBeforeSyncGenerator() {
        AsyncCallbackFramework framework = framework();
        List<String> triggered = new ArrayList<>();
        framework.on("before_gen").apply(kwargs -> {
            triggered.add("fired");
            return null;
        });
        Function<Map<String, Object>, Object> generate = framework.emitBefore("before_gen", false, Map.of())
                .apply(kwargs -> List.of(1, 2, 3).iterator());

        List<Object> items = toList(generate.apply(Map.of()));

        assertEquals(List.of(1, 2, 3), items);
        assertEquals(List.of("fired"), triggered);
    }

    private void emitAfterSyncFunction() {
        AsyncCallbackFramework framework = framework();
        List<Object> receivedResults = new ArrayList<>();
        framework.on("after_sync").apply(kwargs -> {
            receivedResults.add(kwargs.get("result"));
            return null;
        });
        Function<Map<String, Object>, Object> compute = framework.emitAfter(
                "after_sync", "result", "item", false, "per_item", Map.of())
                .apply(kwargs -> ((Integer) args(kwargs)[0]) + 1);

        Object result = compute.apply(kwargsWithArgs(10));

        assertEquals(11, result);
        assertEquals(List.of(11), receivedResults);
    }

    private void emitAfterSyncGeneratorPerItem() {
        AsyncCallbackFramework framework = framework();
        List<Object> receivedItems = new ArrayList<>();
        framework.on("after_item").apply(kwargs -> {
            receivedItems.add(kwargs.get("item"));
            return null;
        });
        Function<Map<String, Object>, Object> generate = framework.emitAfter(
                "after_item", "result", "item", false, "per_item", Map.of())
                .apply(kwargs -> List.of("a", "b").iterator());

        List<Object> items = toList(generate.apply(Map.of()));

        assertEquals(List.of("a", "b"), items);
        assertEquals(List.of("a", "b"), receivedItems);
    }

    private void emitAfterSyncGeneratorOnce() {
        AsyncCallbackFramework framework = framework();
        List<Object> received = new ArrayList<>();
        framework.on("after_all").apply(kwargs -> {
            received.add(kwargs.get("result"));
            return null;
        });
        Function<Map<String, Object>, Object> generate = framework.emitAfter(
                "after_all", "result", "item", false, "once", Map.of())
                .apply(kwargs -> List.of(10, 20).iterator());

        List<Object> items = toList(generate.apply(Map.of()));

        assertEquals(List.of(10, 20), items);
        assertEquals(List.of(List.of(10, 20)), received);
    }

    private void emitAfterSyncGeneratorPerItemPassArgs() {
        AsyncCallbackFramework framework = framework();
        List<Map<String, Object>> received = new ArrayList<>();
        framework.on("after_item_args").apply(kwargs -> {
            received.add(Map.of(
                    "args", args(kwargs),
                    "kwargs", withoutInternalArgs(kwargs)
            ));
            return null;
        });
        Function<Map<String, Object>, Object> generate = framework.emitAfter(
                "after_item_args", "result", "item", true, "per_item", Map.of())
                .apply(kwargs -> List.of(
                        args(kwargs)[0] + String.valueOf(kwargs.getOrDefault("sep", "-")) + "1",
                        args(kwargs)[0] + String.valueOf(kwargs.getOrDefault("sep", "-")) + "2"
                ).iterator());

        List<Object> items = toList(generate.apply(kwargsWithArgsAndKwargs(new Object[]{"val"}, Map.of("sep", ":"))));

        assertEquals(List.of("val:1", "val:2"), items);
        assertEquals(2, received.size());
        for (Map<String, Object> entry : received) {
            assertArrayEquals(new Object[]{"val"}, (Object[]) entry.get("args"));
            assertEquals(":", ((Map<?, ?>) entry.get("kwargs")).get("sep"));
        }
        assertEquals("val:1", ((Map<?, ?>) received.get(0).get("kwargs")).get("item"));
        assertEquals("val:2", ((Map<?, ?>) received.get(1).get("kwargs")).get("item"));
    }

    private void emitAfterGenPerItemPassArgs() {
        AsyncCallbackFramework framework = framework();
        List<Integer> receivedArgs = new ArrayList<>();
        List<Object> receivedResult = new ArrayList<>();
        framework.on("end", 50, false, "default", null, null, 0, 0.0, null, "")
                .apply(kwargs -> {
                    receivedArgs.add((Integer) args(kwargs)[0]);
                    receivedResult.add(kwargs.get("item"));
                    return null;
                });
        framework.on("complete", 50, false, "default", null, null, 0, 0.0, null, "")
                .apply(kwargs -> {
                    receivedArgs.add((Integer) args(kwargs)[0]);
                    receivedResult.add(kwargs.get("result"));
                    return null;
                });
        Function<Map<String, Object>, Object> generate = framework.emitAfter(
                "end", "result", "item", true, "per_item", Map.of())
                .apply(kwargs -> List.of(0, 2, 4).iterator());

        toList(generate.apply(kwargsWithArgs(3)));

        assertEquals(List.of(3, 3, 3), receivedArgs);
        assertEquals(List.of(0, 2, 4), receivedResult);
    }

    private void emitAroundSyncFunction() {
        AsyncCallbackFramework framework = framework();
        List<List<Object>> events = new ArrayList<>();
        framework.on("start").apply(kwargs -> {
            events.add(List.of("start", args(kwargs)[0]));
            return null;
        });
        framework.on("end").apply(kwargs -> {
            events.add(List.of("end", kwargs.get("result")));
            return null;
        });
        Function<Map<String, Object>, Object> compute = framework.emitAround("start", "end", true, true, null)
                .apply(kwargs -> ((Integer) args(kwargs)[0]) * 3);

        Object result = compute.apply(kwargsWithArgs(4));

        assertEquals(12, result);
        assertEquals(List.of("start", 4), events.get(0));
        assertEquals(List.of("end", 12), events.get(1));
    }

    private void emitAroundSyncFunctionError() {
        AsyncCallbackFramework framework = framework();
        List<String> errors = new ArrayList<>();
        framework.on("err").apply(kwargs -> {
            errors.add(String.valueOf(kwargs.get("error")));
            return null;
        });
        Function<Map<String, Object>, Object> failing = framework.emitAround("s", "e", true, true, "err")
                .apply(kwargs -> {
                    throw new IllegalArgumentException("boom");
                });

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> failing.apply(Map.of()));

        assertEquals("boom", error.getMessage());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("boom"));
    }

    private void emitAroundSyncGenerator() {
        AsyncCallbackFramework framework = framework();
        List<Object> events = new ArrayList<>();
        framework.on("gen_start").apply(kwargs -> {
            events.add("start");
            return null;
        });
        framework.on("gen_end").apply(kwargs -> {
            events.add(List.of("end", kwargs.get("result")));
            return null;
        });
        Function<Map<String, Object>, Object> generate = framework.emitAround("gen_start", "gen_end", false, true, null)
                .apply(kwargs -> List.of("x", "y").iterator());

        List<Object> items = toList(generate.apply(Map.of()));

        assertEquals(List.of("x", "y"), items);
        assertEquals("start", events.get(0));
        assertEquals(List.of("end", List.of("x", "y")), events.get(1));
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static Map<String, Object> kwargsWithArgs(Object... args) {
        return kwargsWithArgsAndKwargs(args, Map.of());
    }

    private static Map<String, Object> kwargsWithArgsAndKwargs(Object[] args, Map<String, Object> kwargs) {
        Map<String, Object> values = new LinkedHashMap<>(kwargs);
        values.put("_args", args);
        return values;
    }

    private static Object[] args(Map<String, Object> kwargs) {
        Object value = kwargs.get("_args");
        return value instanceof Object[] objects ? objects : new Object[0];
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toList(Object value) {
        if (value instanceof Iterator<?> iterator) {
            List<Object> items = new ArrayList<>();
            iterator.forEachRemaining(items::add);
            return items;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            iterable.forEach(items::add);
            return items;
        }
        assertInstanceOf(List.class, value);
        return new ArrayList<>((List<Object>) value);
    }

    private static Map<String, Object> withoutInternalArgs(Map<String, Object> kwargs) {
        Map<String, Object> visible = new LinkedHashMap<>(kwargs);
        visible.remove("_args");
        return visible;
    }
}
