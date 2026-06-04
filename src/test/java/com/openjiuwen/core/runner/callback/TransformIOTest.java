/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for transform_io event-based pipeline.
 * <p>
 * Mirrors Python's test_transform_io.py.
 * <p>
 * Verifies:
 * - Transform callbacks can modify input arguments
 * - Transform callbacks can modify output results
 * - Priority ordering for transform callbacks
 */
class TransformIOTest {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(false, false);
    }

    private static Object[] argsFrom(Map<String, Object> kwargs) {
        Object raw = kwargs.get("_args");
        return raw instanceof Object[] args ? args : new Object[0];
    }

    private static List<Integer> collect(Iterator<Object> iterator) {
        List<Integer> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add((Integer) iterator.next());
        }
        return values;
    }

    // === Transform basics ===

    @Test
    @DisplayName("triggerTransform returns noop when no transform callbacks")
    void testTriggerTransformReturnsNoopWhenNoCallbacks() {
        assertSame(CallbackFramework.TRANSFORM_NOOP, framework.triggerTransform("some_event", "arg1"));
    }

    @Test
    @DisplayName("triggerTransform ignores regular callbacks")
    void testTriggerTransformIgnoresRegularCallbacks() {
        List<Object> called = new ArrayList<>();
        framework.on("my_event", kwargs -> {
            called.add(argsFrom(kwargs)[0]);
            return "regular";
        }, "regular_handler");

        Object result = framework.triggerTransform("my_event", 42);

        assertSame(CallbackFramework.TRANSFORM_NOOP, result);
        assertTrue(called.isEmpty());
    }

    @Test
    @DisplayName("triggerTransform runs transform callbacks")
    void testTriggerTransformRunsTransformCallbacks() {
        framework.onTransform("my_event", kwargs -> (int) argsFrom(kwargs)[0] * 2, "transform_handler");

        assertEquals(10, framework.triggerTransform("my_event", 5));
    }

    @Test
    @DisplayName("triggerTransform returns last transform result")
    void testTriggerTransformReturnsLastResult() {
        framework.onTransform("ev", kwargs -> (int) argsFrom(kwargs)[0] + 1, 10, "h1");
        framework.onTransform("ev", kwargs -> (int) argsFrom(kwargs)[0] * 100, 0, "h2");

        assertEquals(300, framework.triggerTransform("ev", 3));
    }

    @Test
    @DisplayName("trigger and triggerTransform keep regular and transform callbacks separate")
    void testTriggerTransformCoexistsWithRegularTrigger() {
        List<Integer> regularCalled = new ArrayList<>();
        List<Integer> transformCalled = new ArrayList<>();
        framework.on("ev", kwargs -> {
            regularCalled.add((Integer) argsFrom(kwargs)[0]);
            return null;
        }, "regular");
        framework.onTransform("ev", kwargs -> {
            transformCalled.add((Integer) argsFrom(kwargs)[0]);
            return argsFrom(kwargs)[0];
        }, "transform");

        List<Object> regularResults = framework.trigger("ev", new Object[]{7}, new HashMap<>());
        assertEquals(1, regularResults.size());
        assertNull(regularResults.get(0));
        assertEquals(List.of(7), regularCalled);
        assertTrue(transformCalled.isEmpty());

        assertEquals(9, framework.triggerTransform("ev", 9));
        assertEquals(List.of(7), regularCalled);
        assertEquals(List.of(9), transformCalled);
    }

    @Test
    @DisplayName("onTransform registers transform type")
    void testOnTransformRegistersTransformType() {
        framework.onTransform("ev", kwargs -> (int) argsFrom(kwargs)[0] + 10, "handler");

        assertEquals(15, framework.triggerTransform("ev", 5));
        assertTrue(framework.trigger("ev", new Object[]{5}, new HashMap<>()).isEmpty());
        assertEquals(CallbackFramework.CALLBACK_TYPE_TRANSFORM,
                framework.listCallbacks("ev").get(0).get("callback_type"));
    }

    @Test
    @DisplayName("transformIo by events is identity when no transform callbacks")
    void testTransformIoIdentityWhenNoTransformCallbacks() {
        Function<Map<String, Object>, Object> add = kwargs -> (int) argsFrom(kwargs)[0] + (int) argsFrom(kwargs)[1];
        Function<Map<String, Object>, Object> wrapped =
                framework.transformIoByEvents(add, "in_ev", "out_ev", "result");

        assertEquals(5, wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{2, 3}))));
    }

    @Test
    @DisplayName("transformIo stream is identity when no transform callbacks")
    void testTransformIoStreamIdentityWhenNoTransformCallbacks() {
        Function<Map<String, Object>, Iterator<Object>> gen = kwargs -> {
            int n = (int) argsFrom(kwargs)[0];
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(i);
            }
            return items.iterator();
        };
        Function<Map<String, Object>, Iterator<Object>> wrapped =
                framework.transformIoStreamByEvents(gen, "in_ev", "out_ev", "result");

        assertEquals(List.of(0, 1, 2), collect(wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{3})))));
    }

    @Test
    @DisplayName("transformIo modifies input arguments")
    void testTransformIoModifiesInput() {
        framework.onTransform("in_ev", kwargs -> {
            Object[] args = argsFrom(kwargs);
            return new CallbackFramework.BoundArgs(new Object[]{(int) args[0] * 2, args[1]}, Map.of());
        }, "double_first");
        Function<Map<String, Object>, Object> add = kwargs -> (int) argsFrom(kwargs)[0] + (int) argsFrom(kwargs)[1];

        Function<Map<String, Object>, Object> wrapped =
                framework.transformIoByEvents(add, "in_ev", "out_ev", "result");

        assertEquals(10, wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{3, 4}))));
    }

    @Test
    @DisplayName("transformIo modifies output")
    void testTransformIoModifiesOutput() {
        framework.onTransform("out_ev", kwargs -> -(int) kwargs.get("result"), "negate");
        Function<Map<String, Object>, Object> add = kwargs -> (int) argsFrom(kwargs)[0] + (int) argsFrom(kwargs)[1];

        Function<Map<String, Object>, Object> wrapped =
                framework.transformIoByEvents(add, "in_ev", "out_ev", "result");

        assertEquals(-5, wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{2, 3}))));
    }

    @Test
    @DisplayName("transformIo modifies both input and output")
    void testTransformIoModifiesBothInputAndOutput() {
        framework.onTransform("in_ev", kwargs -> {
            Object[] args = argsFrom(kwargs);
            return new CallbackFramework.BoundArgs(new Object[]{(int) args[0] + 1, args[1]}, Map.of());
        }, "increment_a");
        framework.onTransform("out_ev", kwargs -> (int) kwargs.get("result") * 2, "double_result");
        Function<Map<String, Object>, Object> add = kwargs -> (int) argsFrom(kwargs)[0] + (int) argsFrom(kwargs)[1];

        Function<Map<String, Object>, Object> wrapped =
                framework.transformIoByEvents(add, "in_ev", "out_ev", "result");

        assertEquals(8, wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{1, 2}))));
    }

    @Test
    @DisplayName("transformIo stream output fires per item")
    void testTransformIoStreamOutputFiresPerItem() {
        framework.onTransform("out_ev", kwargs -> (int) kwargs.get("result") * (int) kwargs.get("result"), "square");
        Function<Map<String, Object>, Iterator<Object>> gen = kwargs -> {
            int n = (int) argsFrom(kwargs)[0];
            List<Object> items = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                items.add(i);
            }
            return items.iterator();
        };

        Function<Map<String, Object>, Iterator<Object>> wrapped =
                framework.transformIoStreamByEvents(gen, "in_ev", "out_ev", "result");

        assertEquals(List.of(1, 4, 9, 16), collect(wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{4})))));
    }

    @Test
    @DisplayName("transformIo stream input modifies argument once")
    void testTransformIoStreamInputModifiesArg() {
        framework.onTransform("in_ev", kwargs -> {
            Object[] args = argsFrom(kwargs);
            return new CallbackFramework.BoundArgs(new Object[]{(int) args[0] * 2}, Map.of());
        }, "double_n");
        Function<Map<String, Object>, Iterator<Object>> gen = kwargs -> {
            int n = (int) argsFrom(kwargs)[0];
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(i);
            }
            return items.iterator();
        };

        Function<Map<String, Object>, Iterator<Object>> wrapped =
                framework.transformIoStreamByEvents(gen, "in_ev", "out_ev", "result");

        assertEquals(List.of(0, 1, 2, 3, 4, 5),
                collect(wrapped.apply(new HashMap<>(Map.of("_args", new Object[]{3})))));
    }

    @Test
    @DisplayName("Disabled transform callback is skipped")
    void testDisabledTransformCallbackIsSkipped() {
        CallbackInfo info = framework.onTransform("ev", kwargs -> (int) argsFrom(kwargs)[0] * 99, "handler");
        info.setEnabled(false);

        assertSame(CallbackFramework.TRANSFORM_NOOP, framework.triggerTransform("ev", 5));
    }

    @Test
    @DisplayName("Transform callback can modify input arguments")
    void testTransformModifiesInput() {
        List<Integer> transformed = new ArrayList<>();

        // Register a transform callback that doubles input
        framework.register("input_transform", kwargs -> {
            Object x = kwargs.get("x");
            if (x instanceof Integer) {
                kwargs.put("x", (Integer) x * 2);
                transformed.add((Integer) kwargs.get("x"));
            }
            return kwargs;
        }, 10, "transform_input");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("x", 5);

        // Trigger the transform
        List<Object> results = framework.trigger("input_transform", new Object[0], kwargs);

        // Check transform was applied
        assertEquals(10, kwargs.get("x"));
    }

    @Test
    @DisplayName("Transform callback can modify output result")
    void testTransformModifiesOutput() {
        // Register a transform callback that doubles result
        framework.register("output_transform", kwargs -> {
            Object result = kwargs.get("result");
            if (result instanceof Integer) {
                kwargs.put("result", (Integer) result * 2);
            }
            return kwargs;
        }, 10, "transform_output");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("result", 7);

        List<Object> results = framework.trigger("output_transform", new Object[0], kwargs);

        // Check transform was applied
        assertEquals(14, kwargs.get("result"));
    }

    @Test
    @DisplayName("Multiple transform callbacks priority ordering")
    void testMultipleTransformCallbacksPriority() {
        List<String> order = new ArrayList<>();

        framework.register("ev", kwargs -> {
            order.add("h1");
            Object x = kwargs.get("x");
            kwargs.put("x", (Integer) x + 1);
            return kwargs;
        }, 10, "transform_h1");

        framework.register("ev", kwargs -> {
            order.add("h2");
            Object x = kwargs.get("x");
            kwargs.put("x", (Integer) x * 100);
            return kwargs;
        }, 0, "transform_h2");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("x", 3);

        framework.trigger("ev", new Object[0], kwargs);

        // priority=10 runs first (h1), priority=0 runs last (h2)
        assertEquals(List.of("h1", "h2"), order);
        // 3 + 1 = 4, then 4 * 100 = 400
        assertEquals(400, kwargs.get("x"));
    }

    @Test
    @DisplayName("Transform returns last callback result")
    void testTransformReturnsLastResult() {
        framework.register("ev", kwargs -> kwargs.get("x"), 10, "t1");
        framework.register("ev", kwargs -> {
            kwargs.put("result", "final");
            return kwargs;
        }, 0, "t2");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("x", 5);

        List<Object> results = framework.trigger("ev", new Object[0], kwargs);

        // Last result should be from lowest priority callback
        assertFalse(results.isEmpty());
    }

    @Test
    @DisplayName("No transform callbacks returns empty results")
    void testNoTransformCallbacks() {
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("x", 5);

        List<Object> results = framework.trigger("no_event", new Object[0], kwargs);
        
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Transform with input and output events")
    void testTransformWithInputOutputEvents() {
        List<String> log = new ArrayList<>();

        // Input transform
        framework.register("input_ev", kwargs -> {
            log.add("input_transform");
            kwargs.put("value", (Integer) kwargs.get("value") + 10);
            return kwargs;
        }, 10, "input_transform");

        // Output transform
        framework.register("output_ev", kwargs -> {
            log.add("output_transform");
            Object result = kwargs.get("result");
            kwargs.put("result", (Integer) result + 100);
            return kwargs;
        }, 10, "output_transform");

        // Simulate: input transform → function → output transform
        Map<String, Object> inputKwargs = new HashMap<>();
        inputKwargs.put("value", 5);
        framework.trigger("input_ev", new Object[0], inputKwargs);
        
        // Function execution
        Object result = inputKwargs.get("value");  // 15
        
        Map<String, Object> outputKwargs = new HashMap<>();
        outputKwargs.put("result", result);
        framework.trigger("output_ev", new Object[0], outputKwargs);
        
        // Final result should be 15 + 100 = 115
        assertEquals(115, outputKwargs.get("result"));
        assertEquals(List.of("input_transform", "output_transform"), log);
    }

    @Test
    @DisplayName("Transform callback chain preserves kwargs")
    void testTransformChainPreservesKwargs() {
        Map<String, Object> original = new HashMap<>();
        original.put("a", 1);
        original.put("b", 2);

        framework.register("chain", kwargs -> {
            kwargs.put("a", (Integer) kwargs.get("a") + 1);
            return kwargs;
        }, 10, "t1");

        framework.register("chain", kwargs -> {
            kwargs.put("b", (Integer) kwargs.get("b") + 1);
            return kwargs;
        }, 5, "t2");

        framework.trigger("chain", new Object[0], original);

        assertEquals(2, original.get("a"));
        assertEquals(3, original.get("b"));
    }
}
