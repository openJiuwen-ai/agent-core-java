/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Manages the stream lifecycle for a single graph vertex, coordinating stream-in
 * abilities (COLLECT/TRANSFORM) with message producers.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.base.StreamActor}.
 * Uses Virtual Threads and CompletableFuture instead of asyncio tasks.
 * 
 * @since 0.1.7
 */
public class StreamActor {
    private static final LoggerProtocol logger = Loggers.GRAPH;
    private static final long SHUTDOWN_TIMEOUT_MS = 5000;
    private static final ExecutorService STREAM_EXECUTOR =
            OpenJiuwenExecutors.newCachedThreadPool("stream-actor", false);

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<ComponentAbility, StreamProcessor> processors = new HashMap<>();
    private Future<?> task;
    private CompletableFuture<Void> taskCompletion;
    private CompletableFuture<Void> taskError;
    private final StreamConsumer vertex;
    private final String nodeId;
    private boolean completedSourcesSeeded;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<RunningTask> runningTasks = new ArrayList<>();

    /**
     * StreamActor.
     *
     * @param nodeId nodeId
     * @param vertex vertex
     * @param abilities abilities
     * @param sourceGroups source groups (CNF OR-groups) consumed by this vertex
     * @param streamGeneratorTimeoutSeconds streamGeneratorTimeoutSeconds
     * @since 0.1.7
     */
    public StreamActor(String nodeId, StreamConsumer vertex, List<ComponentAbility> abilities,
            List<Set<String>> sourceGroups, long streamGeneratorTimeoutSeconds) {
        this.nodeId = nodeId;
        this.vertex = vertex;
        for (ComponentAbility ability : abilities) {
            processors.put(ability, new StreamProcessor(nodeId, sourceGroups, streamGeneratorTimeoutSeconds));
        }
    }

    /**
     * Send a stream message to this actor.
     * 
     * @param message the stream message (Map with single producer→content entry)
     * @param sourceAbility the ability that produced this message (STREAM/TRANSFORM)
     * @param firstFrame whether this is the first frame of a new stream
     * @param producerId the ID of the producer node
     * @since 0.1.7
     */
    public synchronized void send(Object message, ComponentAbility sourceAbility, boolean firstFrame,
            String producerId) {
        if (!vertex.shouldHandleMessage()) {
            logger.warning("Discard chunk send from [{}], {}[{}] unable to handle", producerId, nodeId,
                    sourceAbility.name());
            return;
        }

        // Check if we need to start a new stream task
        if (task == null || task.isDone()) {
            if (task != null && task.isDone() && taskFailure(task) != null) {
                logger.warning("Exception occurred while sending chunk of node [{}]", nodeId, taskFailure(task));
            }
            if (taskError != null && taskError.isDone() && taskError.isCompletedExceptionally()) {
                logger.warning("Discard chunk send from [{}], {}[{}] occur exception", producerId, nodeId,
                        sourceAbility.name());
                return;
            }
            if (!firstFrame || !vertex.isDone()) {
                logger.warning("Discard chunk send from [{}], {}[{}] vertex is done", producerId, nodeId,
                        sourceAbility.name());
                return;
            }

            // Start stream call on the vertex
            CountDownLatch latch = new CountDownLatch(1);
            taskError = new CompletableFuture<>();
            taskCompletion = new CompletableFuture<>();
            task = STREAM_EXECUTOR.submit(() -> {
                try {
                    vertex.streamCall(latch, this::errorCallback);
                } finally {
                    taskCompletion.complete(null);
                }
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            logger.debug("Stream actor task node [{}] started", nodeId);

            // Start processors
            for (Map.Entry<ComponentAbility, StreamProcessor> entry : processors.entrySet()) {
                ComponentAbility ability = entry.getKey();
                StreamProcessor processor = entry.getValue();
                CompletableFuture<Void> completion = new CompletableFuture<>();
                Future<?> processorTask = STREAM_EXECUTOR.submit(() -> {
                    try {
                        processor.run(ability);
                    } finally {
                        completion.complete(null);
                    }
                });
                runningTasks.add(new RunningTask(ability, processorTask, completion));
            }
        }

        logger.debug("Send chunk from [{}] to {}[{}]", producerId, nodeId, sourceAbility.name());

        // Dispatch to all processors
        StreamPayload payload = new StreamPayload(message, sourceAbility);
        for (StreamProcessor processor : processors.values()) {
            processor.receive(payload);
        }
    }

    /**
     * Seed source completions restored from a previous interrupted invocation.
     *
     * @param completedSources completed producer-ability keys
     * @since 0.1.7
     */
    public synchronized void seedCompletedSources(Set<String> completedSources) {
        if (completedSourcesSeeded || completedSources == null || completedSources.isEmpty()) {
            return;
        }
        for (StreamProcessor processor : processors.values()) {
            processor.seedCompletedSources(completedSources);
        }
        completedSourcesSeeded = true;
    }

    /**
     * generator.
     * 
     * @param ability ability
     * @param schema schema
     * @param streamCallback streamCallback
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generator(ComponentAbility ability, Map<String, Object> schema,
            Consumer<Object> streamCallback) {
        StreamProcessor processor = processors.get(ability);
        if (processor == null) {
            return Map.of();
        }
        return processor.generator(schema, streamCallback);
    }

    /**
     * Wait until the stream call and all processor tasks complete.
     * 
     * @since 0.1.7
     */
    public synchronized void awaitCompletion() {
        if (taskCompletion != null) {
            awaitCompletion(taskCompletion, "stream actor task");
        }
        for (RunningTask runningTask : runningTasks) {
            awaitCompletion(runningTask.completion(), "stream actor processor " + runningTask.ability().name());
        }
    }

    /**
     * Shutdown the stream actor, cancelling all running tasks.
     * 
     * @since 0.1.7
     */
    public synchronized void shutdown() {
        logger.debug("Begin to shutdown stream actor task for {}", nodeId);
        try {
            if (task != null && !task.isDone() && !task.isCancelled()) {
                task.cancel(true);
            }
            if (taskError != null && !taskError.isDone() && !taskError.isCancelled()) {
                taskError.cancel(true);
            }
            for (RunningTask runningTask : runningTasks) {
                Future<?> future = runningTask.future();
                if (!future.isDone() && !future.isCancelled()) {
                    future.cancel(true);
                }
            }

            if (taskCompletion != null) {
                awaitCompletion(taskCompletion, "stream actor task");
            }
            for (RunningTask runningTask : runningTasks) {
                awaitCompletion(runningTask.completion(), "stream actor processor " + runningTask.ability().name());
            }
            logger.debug("Succeed to shutdown stream actor task for {}", nodeId);
        } finally {
            task = null;
            taskCompletion = null;
            runningTasks.clear();
        }
    }

    /**
     * errorCallback.
     * 
     * @param error error
     * @since 0.1.7
     */
    private void errorCallback(Exception error) {
        if (error != null && taskError != null && !taskError.isDone()) {
            taskError.completeExceptionally(error);
        }
    }

    /**
     * taskFailure.
     * 
     * @param future future
     * @return the result
     * @since 0.1.7
     */
    private static Throwable taskFailure(Future<?> future) {
        try {
            future.get();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (ExecutionException e) {
            return e.getCause() != null ? e.getCause() : e;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * awaitCompletion.
     * 
     * @param completion completion
     * @param taskName taskName
     * @since 0.1.7
     */
    private void awaitCompletion(CompletableFuture<Void> completion, String taskName) {
        try {
            completion.get(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warning("Timed out waiting for {} of node [{}] to stop", taskName, nodeId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Interrupted while waiting for {} of node [{}] to stop", taskName, nodeId);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.warning("Failed while waiting for {} of node [{}] to stop", taskName, nodeId, cause);
        }
    }

    /**
     * RunningTask.
     * 
     * @param ability ability
     * @param future future
     * @param completion completion
     * @since 0.1.7
     */
    private record RunningTask(ComponentAbility ability, Future<?> future, CompletableFuture<Void> completion) {
    }
}
