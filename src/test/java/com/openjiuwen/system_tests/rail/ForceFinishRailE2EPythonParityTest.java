/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.system_tests.rail;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.ForceFinishRequest;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.Rails;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestForceFinishE2E} in
 * {@code tests/system_tests/rail/test_force_finish_rail.py}.
 */
class ForceFinishRailE2EPythonParityTest {

    @Test
    void testBeforeModelCallSkipsLlmAndReturnsResult() {
        Map<String, Object> forced = mapOf("output", "intercepted", "result_type", "answer");
        RecordingContext modelContext = new RecordingContext();
        AtomicInteger llmCalls = new AtomicInteger();
        modelContext.on(AgentCallbackEvent.BEFORE_MODEL_CALL, () -> modelContext.requestForceFinish(forced));

        Object result = Rails.run(modelContext,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    llmCalls.incrementAndGet();
                    return "should not see";
                });

        assertThat(result).isEqualTo(forced);
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    void testAfterModelCallStopsBeforeToolExecution() {
        Map<String, Object> forced = mapOf("output", "stopped_after_model", "result_type", "answer");
        RecordingContext modelContext = new RecordingContext();
        AtomicBoolean toolCalled = new AtomicBoolean();
        modelContext.on(AgentCallbackEvent.AFTER_MODEL_CALL, () -> modelContext.requestForceFinish(forced));

        Rails.run(modelContext,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> "tool_call_response");

        ForceFinishRequest request = modelContext.consumeForceFinish();
        if (request == null) {
            RecordingContext toolContext = new RecordingContext();
            toolContext.on(AgentCallbackEvent.BEFORE_TOOL_CALL, () -> toolCalled.set(true));
            Rails.run(toolContext,
                    AgentCallbackEvent.BEFORE_TOOL_CALL,
                    AgentCallbackEvent.AFTER_TOOL_CALL,
                    AgentCallbackEvent.ON_TOOL_EXCEPTION,
                    () -> 3);
        }

        assertThat(request).isNotNull();
        assertThat(request.getResult()).isEqualTo(forced);
        assertThat(toolCalled).isFalse();
    }

    @Test
    void testAfterToolCallBreaksLoop() {
        Map<String, Object> forced = mapOf("output", "done_after_tool", "result_type", "answer");
        AtomicInteger llmCalls = new AtomicInteger();
        RecordingContext modelContext = new RecordingContext();

        Rails.run(modelContext,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    llmCalls.incrementAndGet();
                    return "tool_call_response";
                });

        RecordingContext toolContext = new RecordingContext();
        toolContext.on(AgentCallbackEvent.AFTER_TOOL_CALL, () -> toolContext.requestForceFinish(forced));
        Rails.run(toolContext,
                AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL,
                AgentCallbackEvent.ON_TOOL_EXCEPTION,
                () -> 7);

        ForceFinishRequest request = toolContext.consumeForceFinish();
        if (request == null) {
            llmCalls.incrementAndGet();
        }

        assertThat(request).isNotNull();
        assertThat(request.getResult()).isEqualTo(forced);
        assertThat(llmCalls).hasValue(1);
    }

    @Test
    void testForceFinishResultVisibleInAfterInvoke() {
        Map<String, Object> forced = mapOf("output", "forced_result", "result_type", "answer");
        InvokeInputs invokeInputs = new InvokeInputs();
        RecordingContext invokeContext = new RecordingContext();
        invokeContext.setInputs(invokeInputs);
        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
        invokeContext.on(AgentCallbackEvent.AFTER_INVOKE,
                () -> captured.set(((InvokeInputs) invokeContext.getInputs()).getResult()));
        RecordingContext modelContext = new RecordingContext();
        modelContext.on(AgentCallbackEvent.BEFORE_MODEL_CALL, () -> modelContext.requestForceFinish(forced));

        Object result = Rails.run(modelContext,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> "nope");
        invokeInputs.setResult(castResult(result));
        invokeContext.fire(AgentCallbackEvent.AFTER_INVOKE);

        assertThat(captured.get()).isEqualTo(forced);
    }

    @Test
    void testForceFinishWithConversationId() {
        Map<String, Object> forced = mapOf("output", "with_conv_id", "result_type", "answer");
        InvokeInputs invokeInputs = new InvokeInputs();
        invokeInputs.setQuery("test");
        invokeInputs.setConversationId("conv_123");
        RecordingContext modelContext = new RecordingContext();
        modelContext.setInputs(invokeInputs);
        modelContext.on(AgentCallbackEvent.BEFORE_MODEL_CALL, () -> modelContext.requestForceFinish(forced));

        Object result = Rails.run(modelContext,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> "nope");

        assertThat(result).isEqualTo(forced);
        assertThat(((InvokeInputs) modelContext.getInputs()).getConversationId()).isEqualTo("conv_123");
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castResult(Object result) {
        return (Map<String, Object>) result;
    }

    private static final class RecordingContext extends AgentCallbackContext {
        private final Map<AgentCallbackEvent, Runnable> hooks = new LinkedHashMap<>();

        private void on(AgentCallbackEvent event, Runnable action) {
            hooks.put(event, action);
        }

        @Override
        public void fire(AgentCallbackEvent event) {
            setEvent(event);
            Runnable action = hooks.get(event);
            if (action != null) {
                action.run();
            }
        }
    }
}
