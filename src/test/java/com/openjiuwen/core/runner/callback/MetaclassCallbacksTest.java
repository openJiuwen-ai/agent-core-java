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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for callback stacking on framework components.
 * <p>
 * Mirrors Python's test_metaclass_callbacks.py.
 * <p>
 * Verifies that:
 * - emit_before fires for INPUT events
 * - emit_after fires for OUTPUT events
 * - trigger() handles callback chain correctly
 * - Transform fires before trigger for the same event
 */
class MetaclassCallbacksTest {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(false, false);
    }

    // === emit_before / emit_after tests ===

    @Test
    @DisplayName("emit_before fires before function execution")
    void testEmitBeforeFiresBeforeExecution() {
        List<String> log = new ArrayList<>();

        // Register before event callback
        framework.register("before_ev", kwargs -> {
            log.add("before");
            return null;
        }, 10, "before_callback");

        // Simulate: before event → function execution
        framework.trigger("before_ev", new Object[0], new HashMap<>());
        log.add("func");

        assertEquals(List.of("before", "func"), log);
    }

    @Test
    @DisplayName("emit_after fires after function execution")
    void testEmitAfterFiresAfterExecution() {
        List<String> log = new ArrayList<>();

        // Register after event callback
        framework.register("after_ev", kwargs -> {
            log.add("after");
            return null;
        }, 10, "after_callback");

        // Simulate: function execution → after event
        log.add("func");
        framework.trigger("after_ev", new Object[0], new HashMap<>());

        assertEquals(List.of("func", "after"), log);
    }

    @Test
    @DisplayName("emit_before receives transformed args")
    void testEmitBeforeReceivesTransformedArgs() {
        // Input transform
        framework.register("input_transform", kwargs -> {
            kwargs.put("value", (Integer) kwargs.getOrDefault("value", 0) + 10);
            return kwargs;
        }, 10, "input_transform");

        // Before callback
        List<Integer> receivedValue = new ArrayList<>();
        framework.register("before_ev", kwargs -> {
            receivedValue.add((Integer) kwargs.get("value"));
            return null;
        }, 10, "before_callback");

        // Apply transform first
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("value", 5);
        framework.trigger("input_transform", new Object[0], kwargs);

        // Then trigger before event with transformed args
        framework.trigger("before_ev", new Object[0], kwargs);

        // Before callback should receive transformed value (15)
        assertEquals(15, receivedValue.get(0));
    }

    @Test
    @DisplayName("emit_after receives transformed result")
    void testEmitAfterReceivesTransformedResult() {
        // Output transform
        framework.register("output_transform", kwargs -> {
            kwargs.put("result", (Integer) kwargs.get("result") + 100);
            return kwargs;
        }, 10, "output_transform");

        // After callback
        List<Integer> receivedResult = new ArrayList<>();
        framework.register("after_ev", kwargs -> {
            receivedResult.add((Integer) kwargs.get("result"));
            return null;
        }, 10, "after_callback");

        // Function returns 7
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("result", 7);
        
        // Apply output transform
        framework.trigger("output_transform", new Object[0], kwargs);
        
        // Then trigger after event with transformed result
        framework.trigger("after_ev", new Object[0], kwargs);

        // After callback should receive transformed result (107)
        assertEquals(107, receivedResult.get(0));
    }

    @Test
    @DisplayName("Trigger skips transform-type callbacks")
    void testTriggerSkipsTransformCallbacks() {
        List<String> log = new ArrayList<>();

        // Regular callback
        framework.register("ev", kwargs -> {
            log.add("regular");
            return null;
        }, 10, "regular_callback");

        // Transform callback (different event key)
        framework.register("transform_ev", kwargs -> {
            log.add("transform");
            return kwargs;
        }, 10, "transform_callback");

        // Trigger regular event
        framework.trigger("ev", new Object[0], new HashMap<>());

        // Only regular callback should fire
        assertEquals(List.of("regular"), log);
    }

    @Test
    @DisplayName("Transform fires before trigger for same logical event")
    void testTransformFiresBeforeTrigger() {
        List<String> log = new ArrayList<>();

        // Transform callback
        framework.register("data_transform", kwargs -> {
            log.add("transform");
            kwargs.put("value", (Integer) kwargs.getOrDefault("value", 0) + 1);
            return kwargs;
        }, 10, "transform");

        // Regular callback
        framework.register("data_process", kwargs -> {
            log.add("process");
            return kwargs;
        }, 10, "process");

        // Transform first
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("value", 5);
        framework.trigger("data_transform", new Object[0], kwargs);

        // Then regular processing
        framework.trigger("data_process", new Object[0], kwargs);

        assertEquals(List.of("transform", "process"), log);
        assertEquals(6, kwargs.get("value"));
    }

    @Test
    @DisplayName("Callback chain with before/after")
    void testCallbackChainWithBeforeAfter() {
        List<String> log = new ArrayList<>();

        framework.register("before", kwargs -> {
            log.add("before_start");
            return null;
        }, 20, "before_start");

        framework.register("before", kwargs -> {
            log.add("before_end");
            return null;
        }, 10, "before_end");

        framework.register("after", kwargs -> {
            log.add("after_start");
            return null;
        }, 20, "after_start");

        framework.register("after", kwargs -> {
            log.add("after_end");
            return null;
        }, 10, "after_end");

        // Execute before → func → after
        framework.trigger("before", new Object[0], new HashMap<>());
        log.add("func");
        framework.trigger("after", new Object[0], new HashMap<>());

        // Priority ordering: higher priority runs first
        assertEquals(List.of("before_start", "before_end", "func", "after_start", "after_end"), log);
    }
}