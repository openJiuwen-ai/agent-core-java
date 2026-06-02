/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.rail;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_force_finish_rail.py} in
 * {@code tests/system_tests/rail/test_force_finish_rail.py}.
 */
public class TestForceFinishRail {

    @Test
    void testBeforeModelCallSkipsLlmAndReturnsResult() {
        Map<String, Object> forced = Map.of("output", "intercepted", "result_type", "answer");
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(forced);
            }
        };
        CountingInvocation invocation = new CountingInvocation();

        Map<String, Object> result = invocation.invokeBeforeModel(rail, "hello");

        assertThat(result).isEqualTo(forced);
        assertThat(invocation.llmCalls).isZero();
    }

    @Test
    void testAfterModelCallStopsBeforeToolExecution() {
        Map<String, Object> forced = Map.of("output", "stopped_after_model", "result_type", "answer");
        List<String> events = new ArrayList<>();
        AgentRail rail = new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(forced);
            }

            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                events.add("before_tool_call");
            }
        };
        CountingInvocation invocation = new CountingInvocation();

        Map<String, Object> result = invocation.invokeAfterModel(rail, "1+2");

        assertThat(result).isEqualTo(forced);
        assertThat(events).doesNotContain("before_tool_call");
        assertThat(invocation.toolCalls).isZero();
    }

    @Test
    void testAfterToolCallBreaksLoop() {
        Map<String, Object> forced = Map.of("output", "done_after_tool", "result_type", "answer");
        AgentRail rail = new AgentRail() {
            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(forced);
            }
        };
        CountingInvocation invocation = new CountingInvocation();

        Map<String, Object> result = invocation.invokeAfterTool(rail, "3+4");

        assertThat(result).isEqualTo(forced);
        assertThat(invocation.llmCalls).isEqualTo(1);
        assertThat(invocation.toolCalls).isEqualTo(1);
    }

    @Test
    void testForceFinishResultVisibleInAfterInvoke() {
        Map<String, Object> forced = Map.of("output", "forced_result", "result_type", "answer");
        List<Map<String, Object>> captured = new ArrayList<>();
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(forced);
            }

            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
                captured.add(((InvokeInputs) ctx.getInputs()).getResult());
            }
        };
        CountingInvocation invocation = new CountingInvocation();

        invocation.invokeBeforeModelAndAfterInvoke(rail, "test");

        assertThat(captured).containsExactly(forced);
    }

    @Test
    void testForceFinishWithConversationId() {
        Map<String, Object> forced = Map.of("output", "with_conv_id", "result_type", "answer");
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                assertThat(((InvokeInputs) ctx.getInputs()).getConversationId()).isEqualTo("conv_123");
                ctx.requestForceFinish(forced);
            }
        };
        CountingInvocation invocation = new CountingInvocation();

        Map<String, Object> result = invocation.invokeBeforeModel(rail, "test", "conv_123");

        assertThat(result).isEqualTo(forced);
    }

    private static final class CountingInvocation {
        private int llmCalls;
        private int toolCalls;

        private Map<String, Object> invokeBeforeModel(AgentRail rail, String query) {
            return invokeBeforeModel(rail, query, null);
        }

        private Map<String, Object> invokeBeforeModel(AgentRail rail, String query, String conversationId) {
            AgentCallbackContext ctx = context(query, conversationId);
            rail.beforeModelCall(ctx);
            if (ctx.hasForceFinishRequest()) {
                return ctx.consumeForceFinish().getResult();
            }
            llmCalls++;
            return Map.of("output", "model result", "result_type", "answer");
        }

        private Map<String, Object> invokeAfterModel(AgentRail rail, String query) {
            AgentCallbackContext ctx = context(query, null);
            llmCalls++;
            rail.afterModelCall(ctx);
            if (ctx.hasForceFinishRequest()) {
                return ctx.consumeForceFinish().getResult();
            }
            rail.beforeToolCall(ctx);
            toolCalls++;
            return Map.of("output", "tool result", "result_type", "answer");
        }

        private Map<String, Object> invokeAfterTool(AgentRail rail, String query) {
            AgentCallbackContext ctx = context(query, null);
            llmCalls++;
            toolCalls++;
            rail.afterToolCall(ctx);
            if (ctx.hasForceFinishRequest()) {
                return ctx.consumeForceFinish().getResult();
            }
            llmCalls++;
            return Map.of("output", "second model result", "result_type", "answer");
        }

        private void invokeBeforeModelAndAfterInvoke(AgentRail rail, String query) {
            Map<String, Object> result = invokeBeforeModel(rail, query);
            AgentCallbackContext afterCtx = AgentCallbackContext.builder()
                    .inputs(InvokeInputs.builder().query(query).result(result).build())
                    .build();
            rail.afterInvoke(afterCtx);
        }

        private AgentCallbackContext context(String query, String conversationId) {
            EventInputs inputs = InvokeInputs.builder()
                    .query(query)
                    .conversationId(conversationId)
                    .build();
            return AgentCallbackContext.builder().inputs(inputs).build();
        }
    }
}
