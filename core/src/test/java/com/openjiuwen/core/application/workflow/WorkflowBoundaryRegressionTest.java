/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow_agent.WorkflowController;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verifies workflow registration and tracing across invocation boundaries.
 *
 * @since 2026-10-20
 */
class WorkflowBoundaryRegressionTest {
    @Test
    void duplicateWorkflowIdentityKeepsFirstProviderAndSingleCandidate() {
        String id = "duplicate-" + UUID.randomUUID();
        Workflow first = new Workflow(WorkflowCard.builder().id(id).version("1").name("first").build());
        Workflow duplicate = new Workflow(WorkflowCard.builder().id(id).version("1").name("second").build());
        WorkflowAgent agent = new WorkflowAgent(WorkflowAgentConfig.builder().id(id).build());

        try {
            agent.addWorkflows(List.of(first, duplicate));
            assertEquals(1, agent.getWorkflows().size());
            assertSame(first, agent.getWorkflows().get(0));
            WorkflowController controller = assertInstanceOf(WorkflowController.class, agent.getController());
            assertEquals(1, controller.getAgentConfig().getWorkflows().size());
        } finally {
            Runner.resourceMgr().removeWorkflow(id + "_1");
        }
    }

    @Test
    void reusedTracerWritesToCurrentStreamManager() {
        Tracer tracer = new Tracer("resume");
        tracer.init(new StreamWriterManager(new StreamEmitter(), List.of(StreamMode.TRACE)));
        StreamWriterManager resumed = new StreamWriterManager(new StreamEmitter(), List.of(StreamMode.TRACE));
        tracer.updateStreamWriterManager(resumed);
        TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan();

        tracer.trigger(Tracer.TRACE_AGENT, "on_llm_start", Map.of("span", span,
                "inputs", Map.of("messages", List.of()),
                "instance_info", Map.of("class_name", "Model", "type", "llm")));

        Object event = resumed.streamIterator(500, 500, false).next();
        TraceSchema trace = assertInstanceOf(TraceSchema.class, event);
        TraceAgentSpan emitted = assertInstanceOf(TraceAgentSpan.class, trace.getPayload());
        assertEquals(span.getInvokeId(), emitted.getInvokeId());
        assertEquals("Model", emitted.getName());
        tracer.trigger(Tracer.TRACE_AGENT, "on_llm_request", Map.of("span", span,
                "llm_params", Map.of("model", "fake")));
        TraceSchema request = assertInstanceOf(TraceSchema.class,
                resumed.streamIterator(500, 500, false).next());
        TraceAgentSpan requestSpan = assertInstanceOf(TraceAgentSpan.class, request.getPayload());
        assertEquals(List.of(Map.of("llm_params", Map.of("model", "fake"))), requestSpan.getOnInvokeData());
    }
}
