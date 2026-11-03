/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/** Regression coverage for traces produced before a workflow fails. */
@Timeout(15)
class WorkflowStreamFailureTest {
    private static final String WORKFLOW_ID = "issue194-stream-failure";
    private static final String WORKFLOW_KEY = WORKFLOW_ID + "_1";
    private static final List<StreamMode> MODES = List.of(StreamMode.OUTPUT, StreamMode.TRACE);

    private WorkflowAgentConfig config;

    @BeforeEach
    void registerWorkflow() {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(WORKFLOW_KEY).name(WORKFLOW_ID).version("1").build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.addWorkflowComp("good", new PassThrough(), Map.of("query", "${start.query}"), null);
        workflow.addWorkflowComp("bad", new FailingComponent(), Map.of("query", "${good.query}"), null);
        workflow.setEndComp("end", new PassThrough(), Map.of("query", "${bad.query}"), null);
        workflow.addConnection("start", "good");
        workflow.addConnection("good", "bad");
        workflow.addConnection("bad", "end");
        assertTrue(Runner.resourceMgr().addWorkflow(workflow.getCard(), () -> workflow, null).isOk());
        config = WorkflowAgentConfig.builder().id("issue194-agent").workflows(List.of(
                WorkflowSchema.builder().id(WORKFLOW_ID).name(WORKFLOW_ID).version("1").build())).build();
    }

    @AfterEach
    void removeWorkflow() {
        Runner.resourceMgr().removeWorkflow(WORKFLOW_KEY, null, TagMatchStrategy.ALL, true);
    }

    @Test
    void controllerReturnsTracesBeforeOriginalWorkflowFailure() {
        Controller controller = createController();
        AgentSession session = new AgentSession("issue194-controller", null, null);
        try {
            Iterator<Object> stream = assertDoesNotThrow(() -> controller.stream(
                    InputEvent.fromUserInput(Map.of("query", "test")), session, MODES));
            assertTracesThenFailure(stream, BaseError.class);
            assertTrue(controller.getTaskScheduler().getSessions().isEmpty());
        } finally {
            controller.stop();
        }
    }

    @Test
    void synchronousControllerInvokeStillFailsAndCleansUp() {
        Controller controller = createController();
        try {
            assertThrows(BaseError.class, () -> controller.invoke(
                    InputEvent.fromUserInput(Map.of("query", "test")),
                    new AgentSession("issue194-controller-invoke", null, null)));
            assertTrue(controller.getTaskScheduler().getSessions().isEmpty());
        } finally {
            controller.stop();
        }
    }

    @Test
    void compatibilityAgentReturnsTracesBeforeFailure() {
        WorkflowAgent agent = new WorkflowAgent(config);
        AgentSession session = new AgentSession("issue194-facade", null, agent.getCard());
        Iterator<Object> stream = assertDoesNotThrow(() -> agent.stream(Map.of("query", "test"), session, MODES));
        assertTracesThenFailure(stream, BaseError.class);
    }

    @Test
    void compatibilityAgentCreatesSessionAndReturnsTracesBeforeFailure() {
        WorkflowAgent agent = new WorkflowAgent(config);
        Iterator<Object> stream = assertDoesNotThrow(() -> agent.stream(
                Map.of("query", "test", "conversation_id", "issue194-auto-session"), null, MODES));
        assertTracesThenFailure(stream, BaseError.class);
    }

    @Test
    void compatibilityAgentWithDefaultModesReportsOriginalFailure() {
        WorkflowAgent agent = new WorkflowAgent(config);
        AgentSession session = new AgentSession("issue194-default-modes", null, agent.getCard());
        Iterator<Object> stream = assertDoesNotThrow(() -> agent.stream(Map.of("query", "test"), session, null));

        // Null modes retain the legacy invoke path, which produces no stream chunks.
        BaseError failure = assertThrows(BaseError.class, stream::hasNext);
        assertTrue(failure.getMessage().contains("bad"));
        assertFalse(stream.hasNext());
    }

    @Test
    void failedSessionDoesNotLeakTracesIntoNextSession() {
        WorkflowAgent agent = new WorkflowAgent(config);
        AgentSession first = new AgentSession("issue194-first", null, agent.getCard());
        Iterator<Object> firstStream = agent.stream(Map.of("query", "first"), first, MODES);
        int firstCount = assertTracesThenFailure(firstStream, BaseError.class);

        AgentSession second = new AgentSession("issue194-second", null, agent.getCard());
        Iterator<Object> secondStream = agent.stream(Map.of("query", "second"), second, MODES);
        assertEquals(firstCount, assertTracesThenFailure(secondStream, BaseError.class));
        assertFalse(firstStream.hasNext());
    }

    @Test
    void runnerReturnsTracesAndRunsPostRunOnFailure() {
        WorkflowAgent agent = new WorkflowAgent(config);
        AgentSession session = new AgentSession("issue194-runner", null, agent.getCard());
        Iterator<Object> stream = assertDoesNotThrow(() -> Runner.runAgentStreaming(
                agent, Map.of("query", "test"), session, null, MODES, null)
                .toCompletableFuture().join());
        assertTracesThenFailure(stream, BaseError.class);
        assertTrue(session.isPostRunDone());
    }

    @Test
    void synchronousInvokeStillFails() {
        WorkflowAgent agent = new WorkflowAgent(config);
        assertThrows(CompletionException.class, () -> agent.invoke(Map.of("query", "test"),
                new AgentSession("issue194-invoke", null, agent.getCard())).toCompletableFuture().join());
    }

    private Controller createController() {
        Controller controller = new Controller();
        controller.init(new BaseCard(config.getId(), "test", "test"),
                new ControllerConfig(), null, new ContextEngine());
        controller.setEventHandler(new WorkflowEventHandler(config, controller.getContextEngine()));
        return controller;
    }

    private static int assertTracesThenFailure(Iterator<Object> stream, Class<? extends RuntimeException> type) {
        List<TraceSchema> traces = new ArrayList<>();
        RuntimeException failure = assertThrows(type, () -> {
            while (stream.hasNext()) {
                Object chunk = stream.next();
                if (chunk instanceof TraceSchema trace) {
                    traces.add(trace);
                }
            }
        });
        assertTrue(traces.size() >= 4, "Produced workflow traces must be observable before failure");
        Throwable original = failure instanceof CompletionException ? failure.getCause() : failure;
        BaseError workflowFailure = assertInstanceOf(BaseError.class, original);
        assertTrue(workflowFailure.getMessage().contains("bad"));
        assertTrue(workflowFailure.toString().contains("100054"));
        assertFalse(stream.hasNext(), "A failed stream must be terminal");
        return traces.size();
    }

    private static class PassThrough extends WorkflowComponent<Object, Object> {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    private static final class FailingComponent extends PassThrough {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            throw new IllegalStateException("issue-194-deterministic-failure");
        }
    }
}
