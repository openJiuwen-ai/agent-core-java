/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import com.openjiuwen.core.singleagent.schema.Artifact;
import com.openjiuwen.core.singleagent.schema.Part;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.InMemoryEventQueue;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aMessage;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aPart;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTask;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskState;
import com.openjiuwen.extensions.a2a.A2ATransformer.RequestContext;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskArtifactUpdateEvent;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for A2A executor bridge behavior.
 *
 * <p>Mirrors Python's {@code A2AAgentExecutor} in
 * {@code openjiuwen/extensions/a2a/a2a_agent_executor.py}.</p>
 */
class A2AAgentExecutorTest {
    @Test
    void executeReturnsWithoutEventsWhenContextIsIncomplete() {
        A2AAgentExecutor executor = new A2AAgentExecutor();
        InMemoryEventQueue queue = new InMemoryEventQueue();
        RequestContext context = new RequestContext();
        context.setTaskId("task-ignored");
        context.setContextId("context-ignored");

        executor.execute(context, queue).toCompletableFuture().join();

        assertThat(queue.getEvents()).isEmpty();
    }

    @Test
    void executePublishesTaskBeforeStatusAndInvokeArtifacts() {
        AtomicReference<Map<String, Object>> capturedPayload = new AtomicReference<>();
        A2AAgentExecutor executor = new A2AAgentExecutor(payload -> {
            capturedPayload.set(payload);
            return CompletableFuture.completedFuture(result(TaskStatus.COMPLETED, artifact("artifact-1", "summary")));
        });
        InMemoryEventQueue queue = new InMemoryEventQueue();

        executor.execute(request("task-1", "conv-1", "hello"), queue).toCompletableFuture().join();

        List<Object> events = new ArrayList<>(queue.getEvents());
        assertThat(capturedPayload.get())
                .containsEntry("query", "hello")
                .containsEntry("task_id", "task-1")
                .containsEntry("sessionId", "conv-1");
        assertThat(events.get(0)).isInstanceOf(A2aTask.class);
        assertThat(events.stream().filter(TaskStatusUpdateEvent.class::isInstance).findFirst())
                .isPresent()
                .get()
                .satisfies(event -> assertThat(events.indexOf(event)).isGreaterThan(0));
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(TaskArtifactUpdateEvent.class);
            TaskArtifactUpdateEvent artifactEvent = (TaskArtifactUpdateEvent) event;
            assertThat(artifactEvent.getArtifact().getArtifactId()).isEqualTo("artifact-1");
            assertThat(artifactEvent.getArtifact().getParts().get(0).getText()).isEqualTo("summary body");
        });
        assertThat(lastStatus(events)).isEqualTo(A2aTaskState.TASK_STATE_COMPLETED);
    }

    @Test
    void executeUsesStreamHandlerBeforeInvokeHandler() {
        AtomicBoolean invokeCalled = new AtomicBoolean(false);
        A2AAgentExecutor executor = new A2AAgentExecutor(
                payload -> {
                    invokeCalled.set(true);
                    return CompletableFuture.completedFuture(result(TaskStatus.COMPLETED, artifact("invoke", "invoke")));
                },
                payload -> List.of(
                        result(TaskStatus.WORKING, artifact("chunk-1", "first")),
                        result(TaskStatus.INPUT_REQUIRED, artifact("chunk-2", "input"))));
        InMemoryEventQueue queue = new InMemoryEventQueue();

        executor.execute(request("task-stream", "conv-stream", "stream"), queue).toCompletableFuture().join();

        assertThat(invokeCalled).isFalse();
        List<Object> events = new ArrayList<>(queue.getEvents());
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(TaskArtifactUpdateEvent.class);
            assertThat(((TaskArtifactUpdateEvent) event).getArtifact().getArtifactId()).isEqualTo("chunk-1");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(TaskArtifactUpdateEvent.class);
            assertThat(((TaskArtifactUpdateEvent) event).getArtifact().getArtifactId()).isEqualTo("chunk-2");
        });
        assertThat(lastStatus(events)).isEqualTo(A2aTaskState.TASK_STATE_INPUT_REQUIRED);
    }

    @Test
    void executeCompletesWhenNoHandlersAreConfigured() {
        A2AAgentExecutor executor = new A2AAgentExecutor();
        InMemoryEventQueue queue = new InMemoryEventQueue();

        executor.execute(request("task-empty", "conv-empty", "hello"), queue).toCompletableFuture().join();

        assertThat(lastStatus(new ArrayList<>(queue.getEvents()))).isEqualTo(A2aTaskState.TASK_STATE_COMPLETED);
        assertThat(queue.getEvents()).noneMatch(TaskArtifactUpdateEvent.class::isInstance);
    }

    @Test
    void executeFailsTaskAndRethrowsWhenInvokeRaises() {
        A2AAgentExecutor executor = new A2AAgentExecutor(payload -> CompletableFuture.failedFuture(
                new IllegalStateException("invoke failed")));
        InMemoryEventQueue queue = new InMemoryEventQueue();

        assertThatThrownBy(() -> executor.execute(request("task-err", "conv-err", "hello"), queue)
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("invoke failed");

        assertThat(lastStatus(new ArrayList<>(queue.getEvents()))).isEqualTo(A2aTaskState.TASK_STATE_FAILED);
    }

    @Test
    void cancelPublishesCanceledStatusWhenTaskIdExists() {
        A2AAgentExecutor executor = new A2AAgentExecutor();
        InMemoryEventQueue queue = new InMemoryEventQueue();
        RequestContext context = new RequestContext();
        context.setTaskId("task-cancel");
        context.setContextId("conv-cancel");

        executor.cancel(context, queue).toCompletableFuture().join();

        assertThat(lastStatus(new ArrayList<>(queue.getEvents()))).isEqualTo(A2aTaskState.TASK_STATE_CANCELED);
    }

    private static RequestContext request(String taskId, String contextId, String text) {
        A2aPart part = new A2aPart();
        part.setText(text);
        A2aMessage message = new A2aMessage();
        message.setTaskId(taskId);
        message.setContextId(contextId);
        message.setParts(List.of(part));
        RequestContext context = new RequestContext();
        context.setMessage(message);
        context.setTaskId(taskId);
        context.setContextId(contextId);
        return context;
    }

    private static AgentResult result(TaskStatus status, Artifact artifact) {
        AgentResult result = new AgentResult();
        result.setStatus(status);
        result.setArtifacts(List.of(artifact));
        return result;
    }

    private static Artifact artifact(String artifactId, String text) {
        Part part = new Part();
        part.setText(text + " body");
        Artifact artifact = new Artifact();
        artifact.setArtifactId(artifactId);
        artifact.setName(text);
        artifact.setParts(List.of(part));
        return artifact;
    }

    private static Object lastStatus(List<Object> events) {
        return events.stream()
                .filter(TaskStatusUpdateEvent.class::isInstance)
                .map(TaskStatusUpdateEvent.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getStatus()
                .getState();
    }
}
