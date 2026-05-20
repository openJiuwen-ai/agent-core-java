/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("system-test")
class WorkflowInterruptSystemTest {

    private record NestedInteractiveWorkflow(Workflow outerWorkflow, Workflow innerWorkflow) {
    }

    static class RecordingStartComponent extends WorkflowComponent {
        private final AtomicInteger invocationCount;

        RecordingStartComponent(AtomicInteger invocationCount) {
            this.invocationCount = invocationCount;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            invocationCount.incrementAndGet();
            return inputs;
        }
    }

    static class InteractiveAnswerComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> userInputs = session.interact("Please provide an answer");
            return Map.of("answer", userInputs.get("answer"));
        }
    }

    static class RawInteractiveAnswerComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object userInputs = session.interact("Please provide a raw answer");
            return Map.of("answer", userInputs);
        }
    }

    static class PassthroughComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    @Test
    @DisplayName("Interactive workflow returns INPUT_REQUIRED and resumes from checkpoint")
    void interactiveWorkflowReturnsInputRequiredAndResumesFromCheckpoint() {
        AtomicInteger startCount = new AtomicInteger();
        Workflow workflow = buildInteractiveWorkflow(startCount);
        String sessionId = UUID.randomUUID().toString();
        Checkpointer checkpointer = CheckpointerFactory.getCheckpointer();
        Store graphStore = checkpointer.graphStore();

        try {
            WorkflowOutput interrupted = workflow.invoke(
                    Map.of("prompt", "Need user input"),
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);

            assertEquals(WorkflowExecutionState.INPUT_REQUIRED, interrupted.getState());
            List<?> chunks = assertInstanceOf(List.class, interrupted.getResult());
            assertFalse(chunks.isEmpty());

            OutputSchema interactionChunk = assertInstanceOf(OutputSchema.class, chunks.get(0));
            assertEquals(Constant.INTERACTION, interactionChunk.getType());
            InteractionOutput interactionOutput = assertInstanceOf(
                    InteractionOutput.class, interactionChunk.getPayload());
            assertEquals("ask", interactionOutput.getId());

            assertTrue(graphStore.get(sessionId, workflow.getCard().getId()).isPresent());
            assertTrue(checkpointer.sessionExists(sessionId));

            InteractiveInput resumeInputs = new InteractiveInput();
            resumeInputs.update("ask", Map.of("answer", "done"));

            WorkflowOutput resumed = workflow.invoke(
                    resumeInputs,
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);

            assertEquals(WorkflowExecutionState.COMPLETED, resumed.getState());
            assertEquals(Map.of("answer", "done"), resumed.getResult());
            assertTrue(graphStore.get(sessionId, workflow.getCard().getId()).isEmpty());
            assertFalse(checkpointer.sessionExists(sessionId));
            assertEquals(1, startCount.get(), "resume should continue from checkpoint instead of restarting");
        } finally {
            checkpointer.release(sessionId);
        }
    }

    @Test
    @DisplayName("Force delete workflow state restarts interrupted workflow")
    void forceDeleteWorkflowStateRestartsInterruptedWorkflow() {
        AtomicInteger startCount = new AtomicInteger();
        Workflow workflow = buildInteractiveWorkflow(startCount);
        String sessionId = UUID.randomUUID().toString();
        Checkpointer checkpointer = CheckpointerFactory.getCheckpointer();

        try {
            WorkflowOutput firstRun = workflow.invoke(
                    Map.of("prompt", "Need user input"),
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);
            assertEquals(WorkflowExecutionState.INPUT_REQUIRED, firstRun.getState());
            assertEquals(1, startCount.get());

            WorkflowOutput restartedRun = workflow.invoke(
                    Map.of("prompt", "Need user input"),
                    new WorkflowSessionApi(
                            null,
                            sessionId,
                            Map.of(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, true)),
                    null);

            assertEquals(WorkflowExecutionState.INPUT_REQUIRED, restartedRun.getState());
            assertEquals(2, startCount.get(), "force delete should re-run the workflow from the start");
            assertNotNull(restartedRun.getResult());
        } finally {
            checkpointer.release(sessionId);
        }
    }

    @Test
    @DisplayName("Nested sub-workflow interrupt resumes with parent namespace checkpoint")
    void nestedSubWorkflowInterruptResumesWithParentNamespaceCheckpoint() {
        AtomicInteger outerStartCount = new AtomicInteger();
        AtomicInteger innerStartCount = new AtomicInteger();
        NestedInteractiveWorkflow nested = buildNestedInteractiveWorkflow(outerStartCount, innerStartCount);
        Workflow workflow = nested.outerWorkflow();
        String sessionId = UUID.randomUUID().toString();
        Checkpointer checkpointer = CheckpointerFactory.getCheckpointer();
        Store graphStore = checkpointer.graphStore();
        String outerWorkflowId = workflow.getCard().getId();
        String innerWorkflowId = nested.innerWorkflow().getCard().getId();
        String nestedNamespace = outerWorkflowId + ":sub:1";

        try {
            WorkflowOutput interrupted = workflow.invoke(
                    Map.of("prompt", "Need nested user input"),
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);

            assertEquals(WorkflowExecutionState.INPUT_REQUIRED, interrupted.getState());
            List<?> chunks = assertInstanceOf(List.class, interrupted.getResult());
            OutputSchema interactionChunk = assertInstanceOf(OutputSchema.class, chunks.get(0));
            InteractionOutput interactionOutput = assertInstanceOf(
                    InteractionOutput.class, interactionChunk.getPayload());
            assertEquals("sub.ask", interactionOutput.getId());

            assertTrue(graphStore.get(sessionId, outerWorkflowId).isPresent());
            assertTrue(graphStore.get(sessionId, nestedNamespace).isPresent(),
                    "nested graph state should be saved under the parent namespace");
            assertTrue(graphStore.get(sessionId, innerWorkflowId).isEmpty(),
                    "nested graph state should not be saved under the inner workflow card id");

            InteractiveInput resumeInputs = new InteractiveInput();
            resumeInputs.update("sub.ask", Map.of("answer", "nested-done"));

            WorkflowOutput resumed = workflow.invoke(
                    resumeInputs,
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);

            assertEquals(WorkflowExecutionState.COMPLETED, resumed.getState());
            assertEquals(Map.of("answer", "nested-done"), resumed.getResult());
            assertEquals(1, outerStartCount.get(), "outer workflow should resume instead of restarting");
            assertEquals(1, innerStartCount.get(), "inner workflow should resume instead of restarting");
            assertTrue(graphStore.get(sessionId, outerWorkflowId).isEmpty());
            assertTrue(graphStore.get(sessionId, nestedNamespace).isEmpty());
            assertTrue(graphStore.get(sessionId, innerWorkflowId).isEmpty());
        } finally {
            checkpointer.release(sessionId);
        }
    }

    @Test
    @DisplayName("Interactive workflow accepts raw interactive input like Python")
    void interactiveWorkflowAcceptsRawInteractiveInput() {
        AtomicInteger startCount = new AtomicInteger();
        Workflow workflow = buildRawInteractiveWorkflow(startCount);
        String sessionId = UUID.randomUUID().toString();
        Checkpointer checkpointer = CheckpointerFactory.getCheckpointer();

        try {
            WorkflowOutput interrupted = workflow.invoke(
                    Map.of("prompt", "Need raw input"),
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);

            assertEquals(WorkflowExecutionState.INPUT_REQUIRED, interrupted.getState());

            WorkflowOutput resumed = workflow.invoke(
                    new InteractiveInput("done"),
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);

            assertEquals(WorkflowExecutionState.COMPLETED, resumed.getState());
            assertEquals(Map.of("answer", "done"), resumed.getResult());
            assertEquals(1, startCount.get(), "raw-input resume should continue from checkpoint");
        } finally {
            checkpointer.release(sessionId);
        }
    }

    @Test
    @DisplayName("Loop + sub-workflow interrupt keeps deep checkpoint namespaces and resumes in place")
    void loopSubWorkflowInterruptKeepsDeepCheckpointNamespaces() {
        AtomicInteger outerStartCount = new AtomicInteger();
        AtomicInteger innerStartCount = new AtomicInteger();
        NestedInteractiveWorkflow nested = buildLoopNestedInteractiveWorkflow(outerStartCount, innerStartCount);
        Workflow workflow = nested.outerWorkflow();
        String sessionId = UUID.randomUUID().toString();
        Checkpointer checkpointer = CheckpointerFactory.getCheckpointer();
        Store graphStore = checkpointer.graphStore();
        String outerWorkflowId = workflow.getCard().getId();
        String loopNamespace = outerWorkflowId + ":loop:1";
        String bodyNamespace = loopNamespace + ":body:1";
        String nestedNamespace = bodyNamespace + ":sub:1";

        try {
            WorkflowOutput interrupted = workflow.invoke(
                    Map.of("prompt", "Need loop nested input"),
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);

            assertEquals(WorkflowExecutionState.INPUT_REQUIRED, interrupted.getState());
            List<?> chunks = assertInstanceOf(List.class, interrupted.getResult());
            OutputSchema interactionChunk = assertInstanceOf(OutputSchema.class, chunks.get(0));
            InteractionOutput interactionOutput = assertInstanceOf(
                    InteractionOutput.class, interactionChunk.getPayload());
            assertEquals("loop.sub.ask", interactionOutput.getId());

            assertTrue(graphStore.get(sessionId, outerWorkflowId).isPresent());
            assertTrue(graphStore.get(sessionId, loopNamespace).isPresent());
            assertTrue(graphStore.get(sessionId, bodyNamespace).isPresent());
            assertTrue(graphStore.get(sessionId, nestedNamespace).isPresent(),
                    "deep nested graph state should be saved under the composed parent namespace");

            InteractiveInput resumeInputs = new InteractiveInput();
            resumeInputs.update(interactionOutput.getId(), Map.of("answer", "nested-loop-done"));

            WorkflowOutput resumed = workflow.invoke(
                    resumeInputs,
                    new WorkflowSessionApi(null, sessionId, Map.of()),
                    null);

            assertEquals(WorkflowExecutionState.COMPLETED, resumed.getState());
            assertEquals(Map.of("answer", "nested-loop-done"), resumed.getResult());
            assertEquals(1, outerStartCount.get(), "outer workflow should resume instead of restarting");
            assertEquals(1, innerStartCount.get(), "inner workflow should resume inside the loop body");
            assertTrue(graphStore.get(sessionId, outerWorkflowId).isEmpty());
            assertTrue(graphStore.get(sessionId, loopNamespace).isEmpty());
            assertTrue(graphStore.get(sessionId, bodyNamespace).isEmpty());
            assertTrue(graphStore.get(sessionId, nestedNamespace).isEmpty());
        } finally {
            checkpointer.release(sessionId);
        }
    }

    private static Workflow buildInteractiveWorkflow(AtomicInteger startCount) {
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new RecordingStartComponent(startCount),
                Map.of("prompt", "${prompt}"), null);
        workflow.addWorkflowComp("ask", new InteractiveAnswerComponent(),
                Map.of("prompt", "${start.prompt}"), null);
        workflow.setEndComp("end", new PassthroughComponent(),
                Map.of("answer", "${ask.answer}"), null);
        workflow.addConnection("start", "ask");
        workflow.addConnection("ask", "end");
        return workflow;
    }

    private static Workflow buildRawInteractiveWorkflow(AtomicInteger startCount) {
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new RecordingStartComponent(startCount),
                Map.of("prompt", "${prompt}"), null);
        workflow.addWorkflowComp("ask", new RawInteractiveAnswerComponent(),
                Map.of("prompt", "${start.prompt}"), null);
        workflow.setEndComp("end", new PassthroughComponent(),
                Map.of("answer", "${ask.answer}"), null);
        workflow.addConnection("start", "ask");
        workflow.addConnection("ask", "end");
        return workflow;
    }

    private static NestedInteractiveWorkflow buildNestedInteractiveWorkflow(
            AtomicInteger outerStartCount, AtomicInteger innerStartCount) {
        Workflow innerWorkflow = new Workflow();
        innerWorkflow.setStartComp("inner_start", new RecordingStartComponent(innerStartCount),
                Map.of("prompt", "${prompt}"), null);
        innerWorkflow.addWorkflowComp("ask", new InteractiveAnswerComponent(),
                Map.of("prompt", "${inner_start.prompt}"), null);
        innerWorkflow.setEndComp("inner_end", new PassthroughComponent(),
                Map.of("answer", "${ask.answer}"), null);
        innerWorkflow.addConnection("inner_start", "ask");
        innerWorkflow.addConnection("ask", "inner_end");

        Workflow outerWorkflow = new Workflow();
        outerWorkflow.setStartComp("start", new RecordingStartComponent(outerStartCount),
                Map.of("prompt", "${prompt}"), null);
        outerWorkflow.addWorkflowComp("sub", new SubWorkflowComponentImpl(innerWorkflow),
                Map.of("prompt", "${start.prompt}"), null);
        outerWorkflow.setEndComp("end", new PassthroughComponent(),
                Map.of("answer", "${sub.answer}"), null);
        outerWorkflow.addConnection("start", "sub");
        outerWorkflow.addConnection("sub", "end");
        return new NestedInteractiveWorkflow(outerWorkflow, innerWorkflow);
    }

    private static NestedInteractiveWorkflow buildLoopNestedInteractiveWorkflow(
            AtomicInteger outerStartCount, AtomicInteger innerStartCount) {
        Workflow innerWorkflow = new Workflow();
        innerWorkflow.setStartComp("inner_start", new RecordingStartComponent(innerStartCount),
                Map.of("prompt", "${prompt}"), null);
        innerWorkflow.addWorkflowComp("ask", new InteractiveAnswerComponent(),
                Map.of("prompt", "${inner_start.prompt}"), null);
        innerWorkflow.setEndComp("inner_end", new PassthroughComponent(),
                Map.of("answer", "${ask.answer}"), null);
        innerWorkflow.addConnection("inner_start", "ask");
        innerWorkflow.addConnection("ask", "inner_end");

        LoopGroup loopGroup = new LoopGroup();
        loopGroup.addWorkflowComp(
                "sub",
                new SubWorkflowComponentImpl(innerWorkflow),
                null,
                Map.of("prompt", "${start.prompt}"),
                null,
                null,
                null,
                null);
        loopGroup.startNodes(List.of("sub"));
        loopGroup.endNodes("sub");

        Workflow outerWorkflow = new Workflow();
        outerWorkflow.setStartComp("start", new RecordingStartComponent(outerStartCount),
                Map.of("prompt", "${prompt}"), null);
        outerWorkflow.addWorkflowComp("loop",
                new LoopComponentImpl(loopGroup, Map.of("answers", "${sub.answer}")),
                Map.of("loop_type", "number", "loop_number", 1),
                null);
        outerWorkflow.setEndComp("end", new PassthroughComponent(),
                Map.of("answer", "${loop.answers[0]}"), null);
        outerWorkflow.addConnection("start", "loop");
        outerWorkflow.addConnection("loop", "end");
        return new NestedInteractiveWorkflow(outerWorkflow, innerWorkflow);
    }
}
