/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.single_agent.schema.AgentResult;
import com.openjiuwen.core.single_agent.schema.Artifact;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aArtifact;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aMessage;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aPart;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTask;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskState;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskStatus;
import com.openjiuwen.extensions.a2a.A2ATransformer.RequestContext;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskArtifactUpdateEvent;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskStatusUpdateEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bridges A2A request/response events and openjiuwen runtime payloads.
 *
 * <p>Mirrors Python's {@code A2AAgentExecutor} in
 * {@code openjiuwen/extensions/a2a/a2a_agent_executor.py}.</p>
 */
public class A2AAgentExecutor {
    private static final Logger LOGGER = Logger.getLogger(A2AAgentExecutor.class.getName());
    private static final EnumSet<TaskStatus> HALT_STATUSES = EnumSet.of(
            TaskStatus.COMPLETED,
            TaskStatus.CANCELED,
            TaskStatus.FAILED,
            TaskStatus.INPUT_REQUIRED);

    private final A2AInvokeHandler invokeHandler;
    private final A2AStreamHandler streamHandler;

    public A2AAgentExecutor() {
        this(null, null);
    }

    public A2AAgentExecutor(A2AInvokeHandler invokeHandler) {
        this(invokeHandler, null);
    }

    public A2AAgentExecutor(A2AInvokeHandler invokeHandler, A2AStreamHandler streamHandler) {
        this.invokeHandler = invokeHandler;
        this.streamHandler = streamHandler;
    }

    public CompletionStage<Void> execute(RequestContext context, EventQueue eventQueue) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(eventQueue, "eventQueue");
        if (context.getMessage() == null || isBlank(context.getTaskId()) || isBlank(context.getContextId())) {
            return CompletableFuture.completedFuture(null);
        }

        String taskId = context.getTaskId();
        String contextId = context.getContextId();
        TaskUpdater updater = new TaskUpdater(eventQueue, taskId, contextId);
        Map<String, Object> requestPayload = A2ATransformer.fromA2aRequest(context);

        try {
            return enqueueInitialTask(eventQueue, context)
                    .thenCompose(ignored -> updater.startWork(newProcessingMessage()))
                    .thenCompose(ignored -> executeHandler(updater, requestPayload))
                    .handle((ignored, throwable) -> {
                        if (throwable == null) {
                            return null;
                        }
                        Throwable cause = unwrap(throwable);
                        if (cause instanceof CancellationException) {
                            cancelQuietly(updater).toCompletableFuture().join();
                            throw new CompletionException(cause);
                        }
                        LOGGER.log(Level.SEVERE, "A2AAgentExecutor.execute failed for task_id=" + taskId, cause);
                        failQuietly(updater).toCompletableFuture().join();
                        throw new CompletionException(cause);
                    });
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "A2AAgentExecutor.execute failed for task_id=" + taskId, exception);
            failQuietly(updater).toCompletableFuture().join();
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletionStage<Void> cancel(RequestContext context, EventQueue eventQueue) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(eventQueue, "eventQueue");
        if (isBlank(context.getTaskId())) {
            return CompletableFuture.completedFuture(null);
        }
        TaskUpdater updater = new TaskUpdater(
                eventQueue,
                context.getTaskId(),
                context.getContextId() == null ? "" : context.getContextId());
        return cancelQuietly(updater);
    }

    private CompletionStage<Void> executeHandler(TaskUpdater updater, Map<String, Object> requestPayload) {
        if (streamHandler != null) {
            return executeStreaming(updater, requestPayload);
        }
        if (invokeHandler != null) {
            return executeInvoke(updater, requestPayload);
        }
        LOGGER.warning("A2AAgentExecutor has no invoke_handler or stream_handler; completing task with no output.");
        return updater.complete();
    }

    private CompletionStage<Void> executeStreaming(TaskUpdater updater, Map<String, Object> requestPayload) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (AgentResult chunk : streamHandler.stream(requestPayload)) {
            chain = chain.thenCompose(ignored -> publishResult(updater, chunk, false))
                    .thenCompose(stop -> stop
                            ? CompletableFuture.failedFuture(new StreamStoppedException())
                            : CompletableFuture.completedFuture(null));
        }
        return chain.handle((ignored, throwable) -> {
            Throwable cause = unwrap(throwable);
            if (cause instanceof StreamStoppedException) {
                return null;
            }
            if (cause != null) {
                throw new CompletionException(cause);
            }
            updater.complete().toCompletableFuture().join();
            return null;
        });
    }

    private CompletionStage<Void> executeInvoke(TaskUpdater updater, Map<String, Object> requestPayload) {
        return invokeHandler.invoke(requestPayload)
                .thenCompose(result -> publishResult(updater, result, true))
                .thenApply(ignored -> null);
    }

    private CompletionStage<Boolean> publishResult(TaskUpdater updater, AgentResult result, boolean finalResult) {
        AgentResult safeResult = result == null ? new AgentResult() : result;
        List<Artifact> artifacts = safeResult.getArtifacts();
        int lastIndex = artifacts.size() - 1;
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int index = 0; index < artifacts.size(); index++) {
            Artifact artifact = artifacts.get(index);
            boolean lastChunk = finalResult && index == lastIndex;
            chain = chain.thenCompose(ignored -> updater.addArtifact(
                    toA2aParts(artifact.getParts()),
                    artifact.getArtifactId(),
                    artifact.getName(),
                    emptyToNull(artifact.getMetadata()),
                    lastChunk));
        }

        TaskStatus status = safeResult.getStatus();
        if (status == null) {
            status = finalResult ? TaskStatus.COMPLETED : TaskStatus.WORKING;
        }
        TaskStatus finalStatus = status;
        boolean terminal = finalResult || HALT_STATUSES.contains(finalStatus);

        return chain.thenCompose(ignored -> {
            if (!terminal) {
                return updater.startWork().thenApply(unused -> false);
            }
            return switch (finalStatus) {
                case FAILED -> updater.failed().thenApply(unused -> true);
                case CANCELED -> updater.cancel().thenApply(unused -> true);
                case INPUT_REQUIRED -> updater.requiresInput().thenApply(unused -> true);
                default -> updater.complete().thenApply(unused -> true);
            };
        });
    }

    private static CompletionStage<Void> enqueueInitialTask(EventQueue eventQueue, RequestContext context) {
        A2aTask task = new A2aTask();
        task.setId(context.getTaskId());
        task.setContextId(context.getContextId());
        task.setStatus(new A2aTaskStatus(A2aTaskState.TASK_STATE_SUBMITTED));
        return eventQueue.enqueueEvent(task);
    }

    private static A2aMessage newProcessingMessage() {
        A2aPart part = new A2aPart();
        part.setText("Processing your request...");
        A2aMessage message = new A2aMessage();
        message.setParts(List.of(part));
        return message;
    }

    private static List<A2aPart> toA2aParts(List<com.openjiuwen.core.single_agent.schema.Part> parts) {
        List<A2aPart> result = new ArrayList<>();
        for (com.openjiuwen.core.single_agent.schema.Part part : parts) {
            result.add(A2ATransformer.toA2aPart(part));
        }
        return result;
    }

    private static Map<String, Object> emptyToNull(Map<String, Object> metadata) {
        return metadata == null || metadata.isEmpty() ? null : metadata;
    }

    private static CompletionStage<Void> cancelQuietly(TaskUpdater updater) {
        try {
            return updater.cancel();
        } catch (RuntimeException exception) {
            if (!isAlreadyTerminal(exception)) {
                throw exception;
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static CompletionStage<Void> failQuietly(TaskUpdater updater) {
        try {
            return updater.failed();
        } catch (RuntimeException exception) {
            if (!isAlreadyTerminal(exception)) {
                throw exception;
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static boolean isAlreadyTerminal(RuntimeException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("already in a terminal state");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        return throwable;
    }

    /**
     * Mirrors Python's {@code A2AInvokeHandler} callable boundary in
     * {@code openjiuwen/extensions/a2a/a2a_agent_executor.py}.
     */
    @FunctionalInterface
    public interface A2AInvokeHandler {
        CompletionStage<AgentResult> invoke(Map<String, Object> requestPayload);
    }

    /**
     * Mirrors Python's {@code A2AStreamHandler} async iterator boundary in
     * {@code openjiuwen/extensions/a2a/a2a_agent_executor.py}.
     */
    @FunctionalInterface
    public interface A2AStreamHandler {
        Iterable<AgentResult> stream(Map<String, Object> requestPayload);
    }

    /**
     * Mirrors Python's {@code EventQueue} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_agent_executor.py}.
     */
    public interface EventQueue {
        CompletionStage<Void> enqueueEvent(Object event);
    }

    /**
     * Test-friendly in-memory event queue matching Python's EventQueue role.
     *
     * <p>Mirrors Python's {@code EventQueue} boundary in
     * {@code openjiuwen/extensions/a2a/a2a_agent_executor.py}.</p>
     */
    public static final class InMemoryEventQueue implements EventQueue {
        private final Queue<Object> events = new ConcurrentLinkedQueue<>();

        @Override
        public CompletionStage<Void> enqueueEvent(Object event) {
            events.add(event);
            return CompletableFuture.completedFuture(null);
        }

        public Queue<Object> getEvents() {
            return events;
        }
    }

    /**
     * Minimal task updater with terminal-state protection.
     *
     * <p>Mirrors Python's {@code TaskUpdater} boundary in
     * {@code openjiuwen/extensions/a2a/a2a_agent_executor.py}.</p>
     */
    public static final class TaskUpdater {
        private final EventQueue eventQueue;
        private final String taskId;
        private final String contextId;
        private boolean terminal;

        public TaskUpdater(EventQueue eventQueue, String taskId, String contextId) {
            this.eventQueue = Objects.requireNonNull(eventQueue, "eventQueue");
            this.taskId = Objects.requireNonNull(taskId, "taskId");
            this.contextId = contextId == null ? "" : contextId;
        }

        public A2aMessage newAgentMessage(List<A2aPart> parts) {
            A2aMessage message = new A2aMessage();
            message.setTaskId(taskId);
            message.setContextId(contextId);
            message.setParts(parts == null ? List.of() : parts);
            return message;
        }

        public CompletionStage<Void> startWork() {
            return startWork(null);
        }

        public CompletionStage<Void> startWork(A2aMessage message) {
            assertNotTerminal();
            TaskStatusUpdateEvent event = statusEvent(A2aTaskState.TASK_STATE_WORKING);
            if (message != null && !message.getParts().isEmpty()) {
                event.setMetadata(Map.of("message", message));
            }
            return eventQueue.enqueueEvent(event);
        }

        public CompletionStage<Void> addArtifact(List<A2aPart> parts,
                                                 String artifactId,
                                                 String name,
                                                 Map<String, Object> metadata,
                                                 boolean lastChunk) {
            assertNotTerminal();
            A2aArtifact artifact = new A2aArtifact();
            artifact.setArtifactId(artifactId);
            artifact.setName(name);
            artifact.setParts(parts == null ? List.of() : parts);
            TaskArtifactUpdateEvent event = new TaskArtifactUpdateEvent();
            event.setTaskId(taskId);
            event.setContextId(contextId);
            event.setArtifact(artifact);
            Map<String, Object> eventMetadata = new LinkedHashMap<>();
            if (metadata != null) {
                eventMetadata.putAll(metadata);
            }
            eventMetadata.put("last_chunk", lastChunk);
            event.setMetadata(eventMetadata);
            return eventQueue.enqueueEvent(event);
        }

        public CompletionStage<Void> complete() {
            return terminalStatus(A2aTaskState.TASK_STATE_COMPLETED);
        }

        public CompletionStage<Void> failed() {
            return terminalStatus(A2aTaskState.TASK_STATE_FAILED);
        }

        public CompletionStage<Void> cancel() {
            return terminalStatus(A2aTaskState.TASK_STATE_CANCELED);
        }

        public CompletionStage<Void> requiresInput() {
            return terminalStatus(A2aTaskState.TASK_STATE_INPUT_REQUIRED);
        }

        private CompletionStage<Void> terminalStatus(A2aTaskState state) {
            assertNotTerminal();
            terminal = true;
            return eventQueue.enqueueEvent(statusEvent(state));
        }

        private TaskStatusUpdateEvent statusEvent(A2aTaskState state) {
            TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
            event.setTaskId(taskId);
            event.setContextId(contextId);
            event.setStatus(new A2aTaskStatus(state));
            return event;
        }

        private void assertNotTerminal() {
            if (terminal) {
                throw new RuntimeException("Task is already in a terminal state");
            }
        }
    }

    /**
     * Mirrors Python's early stream halt control flow in
     * {@code openjiuwen/extensions/a2a/a2a_agent_executor.py}.
     */
    private static final class StreamStoppedException extends RuntimeException {
    }
}
