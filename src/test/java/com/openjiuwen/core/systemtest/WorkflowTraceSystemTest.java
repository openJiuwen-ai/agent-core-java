/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.session.tracer.TraceWorkflowSpan;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag("system-test")
class WorkflowTraceSystemTest {
    static class PassthroughComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    static class RawListComponent extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = inputs instanceof Map<?, ?> ? (Map<String, Object>) inputs : Map.of();
            return List.of("raw", inputMap.get("prompt"));
        }
    }

    @Test
    @DisplayName("Workflow stream emits workflow-level trace start and done spans")
    void workflowStreamEmitsWorkflowLevelTraceSpans() {
        Workflow workflow = buildTraceWorkflow();
        WorkflowSession session = newTraceSession(workflow.getCard().getId(), StreamMode.OUTPUT, StreamMode.TRACE);
        List<Object> streamItems = new ArrayList<>();
        workflow.stream(Map.of("prompt", "trace me"), session, null).forEachRemaining(streamItems::add);

        List<TraceWorkflowSpan> workflowSpans = topLevelWorkflowSpans(streamItems, workflow.getCard().getId());
        assertTrue(workflowSpans.size() >= 2, "workflow-level tracing should emit both start and done spans");

        TraceWorkflowSpan finalSpan = workflowSpans.get(workflowSpans.size() - 1);
        assertEquals(workflow.getCard().getId(), finalSpan.getWorkflowId());
        assertEquals("Trace Workflow", finalSpan.getWorkflowName());
        assertEquals("3.0", finalSpan.getWorkflowVersion());
        assertEquals(Map.of("prompt", "trace me"), finalSpan.getInputs());
        assertEquals(Map.of("answer", "trace me"), finalSpan.getOutputs());
        assertNotNull(finalSpan.getStartTime());
        assertNotNull(finalSpan.getEndTime());
    }

    @Test
    @DisplayName("Workflow trace emits detached span snapshots")
    void workflowTraceEmitsDetachedSpanSnapshots() {
        Workflow workflow = buildTraceWorkflow();
        WorkflowSession session = newTraceSession(workflow.getCard().getId(), StreamMode.OUTPUT, StreamMode.TRACE);
        List<Object> streamItems = new ArrayList<>();
        workflow.stream(Map.of("prompt", "snapshot me"), session, null).forEachRemaining(streamItems::add);

        List<TraceWorkflowSpan> workflowSpans = topLevelWorkflowSpans(streamItems, workflow.getCard().getId());
        assertTrue(workflowSpans.size() >= 2);

        TraceWorkflowSpan startSpan = workflowSpans.get(0);
        TraceWorkflowSpan finalSpan = workflowSpans.get(workflowSpans.size() - 1);

        assertEquals(Map.of("prompt", "snapshot me"), startSpan.getInputs());
        assertNull(startSpan.getEndTime(), "start snapshot should stay immutable after workflow completion");
        assertNull(startSpan.getOutputs(), "start snapshot should not be backfilled with final outputs");
        assertNotNull(finalSpan.getEndTime());
        assertEquals(Map.of("answer", "snapshot me"), finalSpan.getOutputs());
    }

    @Test
    @DisplayName("Workflow trace keeps raw top-level payloads and raw component outputs")
    void workflowTraceKeepsRawTopLevelPayloadsAndRawComponentOutputs() {
        WorkflowSession rawSession = newTraceSession("workflow-raw-payload", StreamMode.TRACE);
        TracerWorkflowUtils.traceWorkflowStart(rawSession, Map.of("components", List.of("alpha", "beta")));
        TracerWorkflowUtils.traceWorkflowDone(rawSession, Map.of("result", "done"));
        rawSession.streamWriterManager().getStreamEmitter().close();

        List<TraceWorkflowSpan> rawWorkflowSpans =
            topLevelWorkflowSpans(rawSession.streamWriterManager().collectStreamOutput(), "workflow-raw-payload");
        assertEquals(List.of("alpha", "beta"), rawWorkflowSpans.get(0).getInputs());
        assertEquals("done", rawWorkflowSpans.get(rawWorkflowSpans.size() - 1).getOutputs());

        Workflow workflow =
            new Workflow(WorkflowCard.builder().id("workflow-trace-raw-" + UUID.randomUUID().toString().substring(0, 8))
                    .name("Trace Raw Workflow").version("4.0").build());
        workflow.setStartComp("start", new PassthroughComponent(), Map.of("prompt", "${prompt}"), null);
        workflow.setEndComp("end", new RawListComponent(), Map.of("prompt", "${start.prompt}"), null);
        workflow.addConnection("start", "end");

        WorkflowSession session = newTraceSession(workflow.getCard().getId(), StreamMode.OUTPUT, StreamMode.TRACE);
        List<Object> streamItems = new ArrayList<>();
        workflow.stream(Map.of("prompt", "trace me"), session, null).forEachRemaining(streamItems::add);

        List<TraceWorkflowSpan> workflowSpans = topLevelWorkflowSpans(streamItems, workflow.getCard().getId());
        TraceWorkflowSpan finalWorkflowSpan = workflowSpans.get(workflowSpans.size() - 1);
        assertEquals(List.of("raw", "trace me"), finalWorkflowSpan.getOutputs());

        List<TraceWorkflowSpan> componentSpans = componentSpans(streamItems, "end");
        assertEquals(List.of("raw", "trace me"), componentSpans.get(componentSpans.size() - 1).getOutputs());
    }

    private static Workflow buildTraceWorkflow() {
        Workflow workflow =
            new Workflow(WorkflowCard.builder().id("workflow-trace-" + UUID.randomUUID().toString().substring(0, 8))
                    .name("Trace Workflow").version("3.0").build());
        workflow.setStartComp("start", new PassthroughComponent(), Map.of("prompt", "${prompt}"), null);
        workflow.setEndComp("end", new PassthroughComponent(), Map.of("answer", "${start.prompt}"), null);
        workflow.addConnection("start", "end");
        return workflow;
    }

    private static WorkflowSession newTraceSession(String workflowId, StreamMode... modes) {
        WorkflowSession session = new WorkflowSession(workflowId, null, UUID.randomUUID().toString(),
                InMemoryState.create(), new CallbackManager());
        session.setStreamWriterManager(StreamWriterManager.createManager(new StreamEmitter(), List.of(modes)));
        Tracer tracer = new Tracer();
        tracer.init(session.streamWriterManager(), session.callbackManager());
        session.setTracer(tracer);
        return session;
    }

    private static List<TraceWorkflowSpan> topLevelWorkflowSpans(List<Object> streamItems, String workflowId) {
        return streamItems.stream().filter(TraceSchema.class::isInstance).map(TraceSchema.class::cast)
                .map(TraceSchema::getPayload).filter(TraceWorkflowSpan.class::isInstance)
                .map(TraceWorkflowSpan.class::cast).filter(span -> workflowId.equals(span.getInvokeId()))
                .filter(span -> span.getParentNodeId() == null || span.getParentNodeId().isEmpty()).toList();
    }

    private static List<TraceWorkflowSpan> componentSpans(List<Object> streamItems, String invokeId) {
        return streamItems.stream().filter(TraceSchema.class::isInstance).map(TraceSchema.class::cast)
                .map(TraceSchema::getPayload).filter(TraceWorkflowSpan.class::isInstance)
                .map(TraceWorkflowSpan.class::cast).filter(span -> invokeId.equals(span.getInvokeId())).toList();
    }
}
