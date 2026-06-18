/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent.rails;

import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.InvokeInputs;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused tests for invoke result adaptation.
 *
 * <p>Mirrors Python's {@code InvokeResultAdapterRail} in
 * {@code openjiuwen/core/application/llm_agent/rails/invoke_result_adapter_rail.py}.</p>
 */
class InvokeResultAdapterRailTest {

    @Test
    void afterInvokeSkipsMissingResult() {
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(new InvokeInputs());

        new InvokeResultAdapterRail().afterInvoke(context).toCompletableFuture().join();

        assertFalse(context.getExtra().containsKey(InvokeResultAdapterRail.INVOKE_RESULT_KEY));
    }

    @Test
    void afterInvokeConvertsAnswerResult() {
        AgentCallbackContext context = contextWithResult(Map.of(
                "result_type", "answer",
                "output", "done"
        ));

        new InvokeResultAdapterRail().afterInvoke(context).toCompletableFuture().join();

        assertEquals(Map.of("output", "done", "result_type", "answer"),
                context.getExtra().get(InvokeResultAdapterRail.INVOKE_RESULT_KEY));
    }

    @Test
    void afterInvokeConvertsErrorResultWithMissingOutputToEmptyString() {
        AgentCallbackContext context = contextWithResult(Map.of("result_type", "error"));

        new InvokeResultAdapterRail().afterInvoke(context).toCompletableFuture().join();

        assertEquals(Map.of("output", "", "result_type", "error"),
                context.getExtra().get(InvokeResultAdapterRail.INVOKE_RESULT_KEY));
    }

    @Test
    void afterInvokeKeepsOnlyPendingInterruptSchema() {
        OutputSchema first = new OutputSchema("__interaction__", 0, new Payload("questioner"));
        OutputSchema second = new OutputSchema("__interaction__", 1, new Payload("reviewer"));
        AgentCallbackContext context = contextWithResult(Map.of(
                "result_type", "interrupt",
                "component_ids", List.of("reviewer"),
                "workflow_execution_state", new WorkflowState(List.of(first, second))
        ));

        new InvokeResultAdapterRail().afterInvoke(context).toCompletableFuture().join();

        Object adapted = context.getExtra().get(InvokeResultAdapterRail.INVOKE_RESULT_KEY);
        assertEquals(List.of(second), adapted);
        assertSame(second, ((List<?>) adapted).get(0));
    }

    @Test
    void afterInvokeKeepsAllInterruptSchemasWhenNoComponentIds() {
        OutputSchema first = new OutputSchema("__interaction__", 0, new Payload("questioner"));
        OutputSchema second = new OutputSchema("__interaction__", 1, new Payload("reviewer"));
        AgentCallbackContext context = contextWithResult(Map.of(
                "result_type", "interrupt",
                "component_ids", List.of(),
                "workflow_execution_state", Map.of("result", List.of(first, second))
        ));

        new InvokeResultAdapterRail().afterInvoke(context).toCompletableFuture().join();

        assertEquals(List.of(first, second),
                context.getExtra().get(InvokeResultAdapterRail.INVOKE_RESULT_KEY));
    }

    private static AgentCallbackContext contextWithResult(Map<String, Object> result) {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setResult(new LinkedHashMap<>(result));
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        return context;
    }

    private static final class WorkflowState {
        private final List<OutputSchema> result;

        private WorkflowState(List<OutputSchema> result) {
            this.result = result;
        }

        public List<OutputSchema> getResult() {
            return result;
        }
    }

    private static final class Payload {
        private final String id;

        private Payload(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
