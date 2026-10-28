/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow_agent.WorkflowController;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the production controller and graph, without a controller that simulates cancellation. */
@Timeout(40)
class WorkflowCancellationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacementStopsNodesAndAllowsSameWorkflowToRestartAndResume(boolean stream) throws Exception {
        String session = UUID.randomUUID().toString();
        BlockingNode node = new BlockingNode();
        WorkflowAgent agent = agent(node);
        CompletableFuture<Object> previous = CompletableFuture.supplyAsync(() -> call(agent, session, "block", stream));
        try {
            assertThat(node.entered.await(10, TimeUnit.SECONDS)).isTrue();
            Object replacement = call(agent, session, "next", stream);
            assertThat(node.exited.getCount()).isZero();
            assertThat(node.wasInterrupted.get()).isTrue();
            assertThat(String.valueOf(replacement)).contains("__interaction__");
            Object cancelled = previous.get(10, TimeUnit.SECONDS);
            if (stream) {
                List<?> chunks = ((List<?>) cancelled).stream().filter(OutputSchema.class::isInstance).toList();
                assertThat(chunks).hasSize(1);
                OutputSchema terminal = (OutputSchema) chunks.get(0);
                assertThat(terminal.getType()).isEqualTo("workflow_final");
                assertThat(((Map<?, ?>) terminal.getPayload()).get("status")).isEqualTo("cancelled");
            } else {
                assertThat(((Map<?, ?>) cancelled).get("status")).isEqualTo("cancelled");
                assertThat(((Map<?, ?>) cancelled).get("conversation_id")).isEqualTo(session);
            }
            InteractiveInput answer = new InteractiveInput();
            answer.update("ask", "accepted");
            assertThat(String.valueOf(call(agent, session, answer, stream))).contains("accepted");
        } finally {
            node.release.countDown();
            previous.get(10, TimeUnit.SECONDS);
            CheckpointerFactory.getCheckpointer().release(session);
        }
    }

    @Test
    void anotherConversationDoesNotCancelRunningWorkflow() throws Exception {
        String session = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        BlockingNode node = new BlockingNode();
        WorkflowAgent agent = agent(node);
        CompletableFuture<Object> previous = CompletableFuture.supplyAsync(() -> call(agent, session, "block", false));
        try {
            assertThat(node.entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(String.valueOf(call(agent, other, "next", false))).contains("__interaction__");
            assertThat(node.wasInterrupted).isFalse();
            assertThat(previous).isNotDone();
        } finally {
            node.release.countDown();
            assertThat(String.valueOf(previous.get(10, TimeUnit.SECONDS))).contains("__interaction__");
            CheckpointerFactory.getCheckpointer().release(session);
            CheckpointerFactory.getCheckpointer().release(other);
        }
    }

    @Test
    void tenantAndCompletedInvocationsAreNotCancelled() {
        WorkflowController controller = new WorkflowController();
        AgentSession first = new AgentSession("same-id", null, null)
                .withTenantContext(TenantContext.builder().tenantId("tenant-a").build());
        AgentSession second = new AgentSession("same-id", null, null)
                .withTenantContext(TenantContext.builder().tenantId("tenant-b").build());
        var a = controller.beginInvocation(Map.of(), first);
        try (var b = controller.beginInvocation(Map.of(), second)) {
            assertThat(a.cancellation().isCancelled()).isFalse();
        }
        a.close();
        try (var next = controller.beginInvocation(Map.of(), first)) {
            assertThat(a.cancellation().isCancelled()).isFalse();
            assertThat(next.cancellation().isCancelled()).isFalse();
        }
    }

    @Test
    void failureReleasesInvocationWithoutTurningItIntoCancellation() {
        String session = UUID.randomUUID().toString();
        WorkflowAgent agent = agent(new BlockingNode());
        try {
            assertThatThrownBy(() -> call(agent, session, "fail", false)).hasMessageContaining("failure-for-test");
            WorkflowController controller = (WorkflowController) agent.getController();
            try (var next = controller.beginInvocation(Map.of("conversation_id", session), null)) {
                assertThat(next.cancellation().isCancelled()).isFalse();
            }
        } finally {
            CheckpointerFactory.getCheckpointer().release(session);
        }
    }

    @Test
    void cancelledResumeDoesNotReplayTheDiscardedAnswer() throws Exception {
        String session = UUID.randomUUID().toString();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WorkflowAgent agent = agent(new BlockingNode(), new WorkflowComponent<Object, Object>() {
            @Override
            public Object invoke(Object inputs, NodeSessionApi activeSession, ModelContext context) {
                Object answer = activeSession.interact("confirm");
                if ("discard-me".equals(answer)) {
                    entered.countDown();
                    try {
                        if (!release.await(20, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Resume was not cancelled");
                        }
                    } catch (InterruptedException cancelled) {
                        Thread.currentThread().interrupt();
                    }
                }
                return Map.of("answer", answer);
            }
        });
        assertThat(String.valueOf(call(agent, session, "next", false))).contains("__interaction__");
        InteractiveInput discarded = new InteractiveInput();
        discarded.update("ask", "discard-me");
        CompletableFuture<Object> previous = CompletableFuture.supplyAsync(() -> call(agent, session, discarded, false));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            InteractiveInput replacement = new InteractiveInput();
            replacement.update("ask", "accepted");
            String result = String.valueOf(call(agent, session, replacement, false));
            assertThat(result).contains("accepted").doesNotContain("discard-me");
            assertThat(((Map<?, ?>) previous.get(10, TimeUnit.SECONDS)).get("status")).isEqualTo("cancelled");
        } finally {
            release.countDown();
            previous.get(10, TimeUnit.SECONDS);
            CheckpointerFactory.getCheckpointer().release(session);
        }
    }

    private WorkflowAgent agent(BlockingNode node) {
        return agent(node, new WorkflowComponent<Object, Object>() {
            @Override
            public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
                return Map.of("answer", session.interact("confirm"));
            }
        });
    }

    private WorkflowAgent agent(BlockingNode node, WorkflowComponent<Object, Object> questioner) {
        String id = UUID.randomUUID().toString();
        Workflow workflow = new Workflow(WorkflowCard.builder().id(id).name(id).version("1").build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.addWorkflowComp("wait", node, Map.of("query", "${start.query}"), null);
        workflow.addWorkflowComp("ask", questioner, Map.of(), null);
        workflow.setEndComp("end", new End(), Map.of("answer", "${ask.answer}"), null);
        workflow.addConnection("start", "wait");
        workflow.addConnection("wait", "ask");
        workflow.addConnection("ask", "end");
        WorkflowAgent agent = new WorkflowAgent(WorkflowAgentConfig.builder().id("agent-" + id).build());
        agent.addWorkflows(List.of(workflow));
        return agent;
    }

    private Object call(WorkflowAgent agent, String session, Object query, boolean stream) {
        Map<String, Object> input = Map.of("query", query, "conversation_id", session);
        if (stream) {
            List<Object> chunks = new ArrayList<>();
            agent.stream(input, null, List.of(StreamMode.OUTPUT)).forEachRemaining(chunks::add);
            return chunks;
        }
        Object result = agent.invoke(input, null).toCompletableFuture().join();
        return result instanceof com.openjiuwen.core.controller.schema.ControllerOutput output ? output.getData() : result;
    }

    private static final class BlockingNode extends WorkflowComponent<Object, Object> {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch exited = new CountDownLatch(1);
        private final AtomicBoolean wasInterrupted = new AtomicBoolean();

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object query = ((Map<?, ?>) inputs).get("query");
            if ("fail".equals(query)) {
                throw new IllegalStateException("failure-for-test");
            }
            if ("block".equals(query)) {
                entered.countDown();
                try {
                    if (!release.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Node was not cancelled");
                    }
                } catch (InterruptedException cancelled) {
                    wasInterrupted.set(true);
                    // Even a component swallowing interruption must not progress to the next node.
                } finally {
                    exited.countDown();
                }
            }
            return inputs;
        }
    }
}
