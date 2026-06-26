/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Missing agent tracer coverage for agent and workflow trace sharing.
 *
 * <p>Mirrors Python's {@code TestAgent.test_agent_workflow_seq_exec_stream_workflow_with_tracer} in
 * {@code tests/unit_tests/core/session/tracer/test_agent.py}.</p>
 */
class AgentTracerMissingTest {

    @Test
    void agentWorkflowSeqExecStreamWorkflowSharesTracer() {
        AgentSession agentSession = AgentSession.createAgentSession(
                "test",
                null,
                new AgentCard("test_agent_checkpoint", "test_agent_checkpoint", "test")
        );
        agentSession.preRun(Map.of());
        try {
            Tracer tracer = agentSession.getInner().tracer();
            TraceAgentSpan agentSpan = tracer.getTracerAgentSpanManager().createAgentSpan();
            triggerAgent(tracer, "on_chain_start", agentSpan, Map.of("input", "mock chain"), null, "Agent");

            TraceAgentSpan llmSpan = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            triggerAgent(tracer, "on_llm_start", llmSpan, Map.of("llm", "mock llm"), null, "Openai");
            triggerAgent(tracer, "on_llm_end", llmSpan, null, Map.of("outputs", "mock llm"), null);

            TraceAgentSpan pluginSpan = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            triggerAgent(tracer, "on_plugin_start", pluginSpan, Map.of("llm", "mock tool"), null, "RestFulAPI");
            triggerAgent(tracer, "on_plugin_end", pluginSpan, null, Map.of("outputs", "mock tool"), null);

            triggerLinearWorkflow(tracer);
            triggerAgent(tracer, "on_chain_end", agentSpan, null, Map.of("outputs", "mock chain"), null);

            TraceWorkflowSpan start = tracer.getWorkflowSpan("start", "");
            TraceWorkflowSpan nodeA = tracer.getWorkflowSpan("a", "");
            TraceWorkflowSpan nodeB = tracer.getWorkflowSpan("b", "");
            TraceWorkflowSpan end = tracer.getWorkflowSpan("end", "");

            assertThat(agentSpan.getName()).isEqualTo("Agent");
            assertThat(agentSpan.getInputs()).containsEntry("input", "mock chain");
            assertThat(agentSpan.getOutputs()).isEqualTo(Map.of("outputs", "mock chain"));
            assertThat(agentSpan.getChildInvokesId()).containsExactly(llmSpan.getInvokeId(), pluginSpan.getInvokeId());
            assertThat(llmSpan.getParentInvokeId()).isEqualTo(agentSpan.getInvokeId());
            assertThat(llmSpan.getName()).isEqualTo("Openai");
            assertThat(llmSpan.getOutputs()).isEqualTo(Map.of("outputs", "mock llm"));
            assertThat(pluginSpan.getParentInvokeId()).isEqualTo(agentSpan.getInvokeId());
            assertThat(pluginSpan.getName()).isEqualTo("RestFulAPI");
            assertThat(pluginSpan.getOutputs()).isEqualTo(Map.of("outputs", "mock tool"));

            assertThat(start.getParentInvokeId()).isNull();
            assertThat(nodeA.getParentInvokeId()).isEqualTo("start");
            assertThat(nodeB.getParentInvokeId()).isEqualTo("a");
            assertThat(end.getParentInvokeId()).isEqualTo("b");
            assertThat(nodeA.getStreamOutputs()).containsExactly(custom("a", 1, "1"), custom("a", 2, "2"));
            assertThat(nodeB.getStreamOutputs()).containsExactly(custom("b", 1, "1"), custom("b", 2, "2"));
            assertThat(end.getOutputs()).isEqualTo(Map.of("result", 1));
        } finally {
            agentSession.postRun();
        }
    }

    private static void triggerAgent(
            Tracer tracer,
            String event,
            TraceAgentSpan span,
            Map<String, Object> inputs,
            Map<String, Object> outputs,
            String className
    ) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("span", span);
        if (inputs != null) {
            kwargs.put("inputs", inputs);
        }
        if (outputs != null) {
            kwargs.put("outputs", outputs);
        }
        if (className != null) {
            kwargs.put("instance_info", Map.of("class_name", className));
        }
        tracer.trigger(Tracer.TRACE_AGENT, event, kwargs);
    }

    private static void triggerLinearWorkflow(Tracer tracer) {
        workflowStart(tracer, "start", component("workflow", "start", "MockStartNode"),
                Map.of("a", 1, "b", "haha"));
        workflowDone(tracer, "start", mapOf("a", 1, "b", "haha", "c", 1, "d", List.of(1, 2, 3)));

        workflowStart(tracer, "a", component("workflow", "a", "StreamNodeWithTracer"),
                Map.of("aa", 1, "ac", 1));
        workflowStream(tracer, "a", custom("a", 1, "1"));
        workflowStream(tracer, "a", custom("a", 2, "2"));
        workflowDone(tracer, "a", Map.of("aa", 1, "ac", 1));

        workflowStart(tracer, "b", component("workflow", "b", "StreamNodeWithTracer"),
                Map.of("ba", 1, "bc", 1));
        workflowStream(tracer, "b", custom("b", 1, "1"));
        workflowStream(tracer, "b", custom("b", 2, "2"));
        workflowDone(tracer, "b", Map.of("ba", 1, "bc", 1));

        workflowStart(tracer, "end", component("workflow", "end", "MockEndNode"), Map.of("result", 1));
        workflowDone(tracer, "end", Map.of("result", 1));
    }

    private static void workflowStart(
            Tracer tracer,
            String invokeId,
            Map<String, Object> metadata,
            Map<String, Object> inputs
    ) {
        tracer.trigger(Tracer.TRACE_WORKFLOW, "on_call_start",
                mapOf("invoke_id", invokeId, "metadata", metadata, "inputs", inputs));
    }

    private static void workflowStream(Tracer tracer, String invokeId, Map<String, Object> chunk) {
        tracer.trigger(Tracer.TRACE_WORKFLOW, "on_post_stream", mapOf("invoke_id", invokeId, "chunk", chunk));
    }

    private static void workflowDone(Tracer tracer, String invokeId, Map<String, Object> outputs) {
        tracer.trigger(Tracer.TRACE_WORKFLOW, "on_call_done", mapOf("invoke_id", invokeId, "outputs", outputs));
    }

    private static Map<String, Object> component(String workflowId, String componentId, String componentType) {
        return mapOf(
                "workflowId", workflowId,
                "componentId", componentId,
                "componentName", componentId,
                "componentType", componentType
        );
    }

    private static Map<String, Object> custom(String nodeId, int id, String data) {
        return mapOf("node_id", nodeId, "id", id, "data", data);
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
