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

    private static Object[] argsFrom(Map<String, Object> kwargs) {
        Object raw = kwargs.get("_args");
        return raw instanceof Object[] args ? args : new Object[0];
    }

    private Object invokeWithCallbacks(String inputEvent, String outputEvent, Object input) {
        return invokeWithCallbacks(inputEvent, outputEvent, input, new HashMap<>());
    }

    private Object invokeWithCallbacks(String inputEvent, String outputEvent, Object input, Map<String, Object> extra) {
        Map<String, Object> inputKwargs = new HashMap<>(extra);
        Object transformedInput = input;
        Object inputTransform = framework.triggerTransform(inputEvent, new Object[]{input}, inputKwargs);
        if (inputTransform instanceof CallbackFramework.BoundArgs boundArgs && boundArgs.getArgs().length > 0) {
            transformedInput = boundArgs.getArgs()[0];
        }
        framework.trigger(inputEvent, new Object[]{transformedInput}, new HashMap<>(extra));

        Object result = transformedInput;
        Map<String, Object> outputKwargs = new HashMap<>(extra);
        outputKwargs.put("result", result);
        Object outputTransform = framework.triggerTransform(outputEvent, new Object[0], outputKwargs);
        if (outputTransform != CallbackFramework.TRANSFORM_NOOP) {
            result = outputTransform;
        }

        Map<String, Object> triggerKwargs = new HashMap<>(extra);
        triggerKwargs.put("result", result);
        framework.trigger(outputEvent, new Object[0], triggerKwargs);
        return result;
    }

    private List<Object> streamWithCallbacks(String inputEvent, String outputEvent, Object input) {
        return streamWithCallbacks(inputEvent, outputEvent, input, new HashMap<>());
    }

    private List<Object> streamWithCallbacks(String inputEvent, String outputEvent, Object input, Map<String, Object> extra) {
        Object transformedInput = input;
        Object inputTransform = framework.triggerTransform(inputEvent, new Object[]{input}, new HashMap<>(extra));
        if (inputTransform instanceof CallbackFramework.BoundArgs boundArgs && boundArgs.getArgs().length > 0) {
            transformedInput = boundArgs.getArgs()[0];
        }
        framework.trigger(inputEvent, new Object[]{transformedInput}, new HashMap<>(extra));

        Map<String, Object> outputKwargs = new HashMap<>(extra);
        outputKwargs.put("result", transformedInput);
        Object transformedOutput = framework.triggerTransform(outputEvent, new Object[0], outputKwargs);
        Object item = transformedOutput == CallbackFramework.TRANSFORM_NOOP ? transformedInput : transformedOutput;

        Map<String, Object> triggerKwargs = new HashMap<>(extra);
        triggerKwargs.put("result", item);
        framework.trigger(outputEvent, new Object[0], triggerKwargs);
        return List.of(item);
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

        // Transform callback on the same event must be skipped by trigger().
        framework.onTransform("ev", kwargs -> {
            log.add("transform");
            return kwargs;
        }, 10, "transform_callback");

        // Trigger regular event
        framework.trigger("ev", new Object[0], new HashMap<>());

        // Only regular callback should fire
        assertEquals(List.of("regular"), log);
    }

    @Test
    @DisplayName("Tool invoke input callback receives transformed input")
    void testToolInvokeEmitBeforeWithTransformedInput() {
        List<Object> received = new ArrayList<>();
        framework.onTransform(ToolCallEvents.TOOL_INVOKE_INPUT,
                kwargs -> new CallbackFramework.BoundArgs(new Object[]{"transformed"}, Map.of()), "t_in");
        framework.on(ToolCallEvents.TOOL_INVOKE_INPUT, kwargs -> {
            received.add(argsFrom(kwargs)[0]);
            return null;
        }, "record_in");

        Object result = invokeWithCallbacks(ToolCallEvents.TOOL_INVOKE_INPUT, ToolCallEvents.TOOL_INVOKE_OUTPUT,
                "original");

        assertEquals(List.of("transformed"), received);
        assertEquals("transformed", result);
    }

    @Test
    @DisplayName("Tool invoke output callback receives transformed output")
    void testToolInvokeEmitAfterWithTransformedOutput() {
        List<Object> received = new ArrayList<>();
        framework.onTransform(ToolCallEvents.TOOL_INVOKE_OUTPUT,
                kwargs -> kwargs.get("result") + "-out", "t_out");
        framework.on(ToolCallEvents.TOOL_INVOKE_OUTPUT, kwargs -> {
            received.add(kwargs.get("result"));
            return null;
        }, "record_out");

        Object result = invokeWithCallbacks(ToolCallEvents.TOOL_INVOKE_INPUT, ToolCallEvents.TOOL_INVOKE_OUTPUT,
                "hello");

        assertEquals("hello-out", result);
        assertEquals(List.of("hello-out"), received);
    }

    @Test
    @DisplayName("Tool invoke input and output transforms are both applied")
    void testToolInvokeBothTransformsApplied() {
        List<Object> inReceived = new ArrayList<>();
        List<Object> outReceived = new ArrayList<>();
        framework.onTransform(ToolCallEvents.TOOL_INVOKE_INPUT,
                kwargs -> new CallbackFramework.BoundArgs(new Object[]{"x"}, Map.of()), "t_in");
        framework.onTransform(ToolCallEvents.TOOL_INVOKE_OUTPUT,
                kwargs -> kwargs.get("result") + "!", "t_out");
        framework.on(ToolCallEvents.TOOL_INVOKE_INPUT, kwargs -> {
            inReceived.add(argsFrom(kwargs)[0]);
            return null;
        }, "record_in");
        framework.on(ToolCallEvents.TOOL_INVOKE_OUTPUT, kwargs -> {
            outReceived.add(kwargs.get("result"));
            return null;
        }, "record_out");

        Object result = invokeWithCallbacks(ToolCallEvents.TOOL_INVOKE_INPUT, ToolCallEvents.TOOL_INVOKE_OUTPUT,
                "original");

        assertEquals("x!", result);
        assertEquals(List.of("x"), inReceived);
        assertEquals(List.of("x!"), outReceived);
    }

    @Test
    @DisplayName("Tool stream input callback receives transformed input")
    void testToolStreamEmitBeforeWithTransformedInput() {
        List<Object> received = new ArrayList<>();
        framework.onTransform(ToolCallEvents.TOOL_STREAM_INPUT,
                kwargs -> new CallbackFramework.BoundArgs(new Object[]{"stream-transformed"}, Map.of()), "t_in");
        framework.on(ToolCallEvents.TOOL_STREAM_INPUT, kwargs -> {
            received.add(argsFrom(kwargs)[0]);
            return null;
        }, "record_in");

        List<Object> items = streamWithCallbacks(ToolCallEvents.TOOL_STREAM_INPUT, ToolCallEvents.TOOL_STREAM_OUTPUT,
                "original");

        assertEquals(List.of("stream-transformed"), received);
        assertEquals(List.of("stream-transformed"), items);
    }

    @Test
    @DisplayName("Tool stream output callback fires per transformed item")
    void testToolStreamEmitAfterPerItemWithTransformedOutput() {
        List<Object> received = new ArrayList<>();
        framework.onTransform(ToolCallEvents.TOOL_STREAM_OUTPUT,
                kwargs -> kwargs.get("result") + "-item", "t_out");
        framework.on(ToolCallEvents.TOOL_STREAM_OUTPUT, kwargs -> {
            received.add(kwargs.get("result"));
            return null;
        }, "record_out");

        List<Object> items = streamWithCallbacks(ToolCallEvents.TOOL_STREAM_INPUT, ToolCallEvents.TOOL_STREAM_OUTPUT,
                "chunk");

        assertEquals(List.of("chunk-item"), items);
        assertEquals(List.of("chunk-item"), received);
    }

    @Test
    @DisplayName("Workflow invoke transform and callbacks fire with correct output")
    void testWorkflowInvokeCallbacks() {
        List<Object> outReceived = new ArrayList<>();
        framework.onTransform(WorkflowEvents.WORKFLOW_INVOKE_OUTPUT,
                kwargs -> kwargs.get("result") + "-wf", "t_out");
        framework.on(WorkflowEvents.WORKFLOW_INVOKE_OUTPUT, kwargs -> {
            outReceived.add(kwargs.get("result"));
            return null;
        }, "record_out");

        Object result = invokeWithCallbacks(WorkflowEvents.WORKFLOW_INVOKE_INPUT,
                WorkflowEvents.WORKFLOW_INVOKE_OUTPUT, "data");

        assertEquals("data-wf", result);
        assertEquals(List.of("data-wf"), outReceived);
    }

    @Test
    @DisplayName("Workflow stream transform and callbacks fire with correct output")
    void testWorkflowStreamCallbacks() {
        List<Object> received = new ArrayList<>();
        framework.onTransform(WorkflowEvents.WORKFLOW_STREAM_OUTPUT,
                kwargs -> kwargs.get("result") + "-wf", "t_out");
        framework.on(WorkflowEvents.WORKFLOW_STREAM_OUTPUT, kwargs -> {
            received.add(kwargs.get("result"));
            return null;
        }, "record_out");

        List<Object> items = streamWithCallbacks(WorkflowEvents.WORKFLOW_STREAM_INPUT,
                WorkflowEvents.WORKFLOW_STREAM_OUTPUT, "data");

        assertEquals(List.of("data-wf"), items);
        assertEquals(List.of("data-wf"), received);
    }

    @Test
    @DisplayName("Model invoke transform and callbacks fire with correct output")
    @SuppressWarnings("unchecked")
    void testModelInvokeCallbacks() {
        List<Object> outReceived = new ArrayList<>();
        framework.onTransform(LLMCallEvents.LLM_INVOKE_OUTPUT, kwargs -> {
            List<Object> result = new ArrayList<>((List<Object>) kwargs.get("result"));
            result.add("extra");
            return result;
        }, "t_out");
        framework.on(LLMCallEvents.LLM_INVOKE_OUTPUT, kwargs -> {
            outReceived.add(kwargs.get("result"));
            return null;
        }, "record_out");

        Object result = invokeWithCallbacks(LLMCallEvents.LLM_INVOKE_INPUT,
                LLMCallEvents.LLM_INVOKE_OUTPUT, List.of("msg"));

        assertEquals(List.of("msg", "extra"), result);
        assertEquals(List.of(List.of("msg", "extra")), outReceived);
    }

    @Test
    @DisplayName("Model stream transform and callbacks fire per item")
    void testModelStreamCallbacks() {
        List<Object> received = new ArrayList<>();
        framework.onTransform(LLMCallEvents.LLM_STREAM_OUTPUT,
                kwargs -> kwargs.get("result") + "-chunk", "t_out");
        framework.on(LLMCallEvents.LLM_STREAM_OUTPUT, kwargs -> {
            received.add(kwargs.get("result"));
            return null;
        }, "record_out");

        List<Object> items = streamWithCallbacks(LLMCallEvents.LLM_STREAM_INPUT,
                LLMCallEvents.LLM_STREAM_OUTPUT, "['msg']");

        assertEquals(List.of("['msg']-chunk"), items);
        assertEquals(List.of("['msg']-chunk"), received);
    }

    @Test
    @DisplayName("Transform callback fires before regular trigger callback")
    void testTriggerAndTransformFireInOrder() {
        List<List<Object>> order = new ArrayList<>();
        framework.onTransform(ToolCallEvents.TOOL_INVOKE_OUTPUT, kwargs -> {
            order.add(List.of("transform", kwargs.get("result")));
            return kwargs.get("result") + "-T";
        }, "t_out");
        framework.on(ToolCallEvents.TOOL_INVOKE_OUTPUT, kwargs -> {
            order.add(List.of("trigger", kwargs.get("result")));
            return null;
        }, "normal_out");

        invokeWithCallbacks(ToolCallEvents.TOOL_INVOKE_INPUT, ToolCallEvents.TOOL_INVOKE_OUTPUT, "v");

        assertEquals(List.of("transform", "v"), order.get(0));
        assertEquals(List.of("trigger", "v-T"), order.get(1));
    }

    @Test
    @DisplayName("Model invoke input handler receives model kwargs")
    void testModelInvokeExtraKwargsInjected() {
        Map<String, Object> captured = new HashMap<>();
        Object modelConfig = new Object();
        Object modelClientConfig = new Object();
        framework.on(LLMCallEvents.LLM_INVOKE_INPUT, kwargs -> {
            captured.putAll(kwargs);
            return null;
        }, "record_kwargs");

        invokeWithCallbacks(LLMCallEvents.LLM_INVOKE_INPUT, LLMCallEvents.LLM_INVOKE_OUTPUT, List.of("msg"),
                Map.of("model_config", modelConfig, "model_client_config", modelClientConfig));

        assertSame(modelConfig, captured.get("model_config"));
        assertSame(modelClientConfig, captured.get("model_client_config"));
    }

    @Test
    @DisplayName("Model stream output handler receives model kwargs")
    void testModelStreamExtraKwargsInjected() {
        Map<String, Object> captured = new HashMap<>();
        Object modelConfig = new Object();
        Object modelClientConfig = new Object();
        framework.on(LLMCallEvents.LLM_STREAM_OUTPUT, kwargs -> {
            captured.putAll(kwargs);
            return null;
        }, "record_kwargs");

        streamWithCallbacks(LLMCallEvents.LLM_STREAM_INPUT, LLMCallEvents.LLM_STREAM_OUTPUT, List.of("msg"),
                Map.of("model_config", modelConfig, "model_client_config", modelClientConfig));

        assertSame(modelConfig, captured.get("model_config"));
        assertSame(modelClientConfig, captured.get("model_client_config"));
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
