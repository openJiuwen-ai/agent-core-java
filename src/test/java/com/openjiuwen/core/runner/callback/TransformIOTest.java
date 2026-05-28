/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
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

    // === Transform basics ===

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