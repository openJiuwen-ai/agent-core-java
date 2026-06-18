/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code StreamActor} in
 * {@code openjiuwen/core/graph/stream_actor/base.py}.
 */
public class StreamActor {

    private static final LoggerProtocol LOGGER = Loggers.GRAPH;
    private static final long SHUTDOWN_TIMEOUT_MS = 5000L;
    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final Map<ComponentAbility, StreamProcessor> processors = new HashMap<>();
    private final StreamConsumer vertex;
    private final String nodeId;
    private final List<RunningTask> runningTasks = new ArrayList<>();

    private Future<?> task;
    private CompletableFuture<Void> taskCompletion;
    private CompletableFuture<Void> taskError;

    public StreamActor(
            String nodeId,
            StreamConsumer vertex,
            List<ComponentAbility> abilities,
            List<List<String>> sourceGroups,
            double streamGeneratorTimeoutSeconds) {
        this.nodeId = nodeId;
        this.vertex = vertex;
        for (ComponentAbility ability : abilities) {
            processors.put(ability, new StreamProcessor(nodeId, sourceGroups, streamGeneratorTimeoutSeconds));
        }
    }

    /**
     * Sends a stream message to the actor, starting the vertex stream call on the first valid frame.
     *
     * @param message stream message
     * @param sourceAbility source component ability
     * @param firstFrame whether this is the first frame
     * @param producerId producer node id for logging
     */
    public synchronized void send(
            Object message,
            ComponentAbility sourceAbility,
            boolean firstFrame,
            String producerId) {
        String abilityName = sourceAbility.getAbilityName();
        if (!vertex.shouldHandleMessage()) {
            LOGGER.warning("Discard chunk send from [{}], {}[{}] unable to handle",
                    producerId, nodeId, abilityName);
            return;
        }

        if (task == null || task.isDone()) {
            taskFailure(task).ifPresent(failure ->
                    LOGGER.warning("Exception occurred while sending chunk of node [{}]", nodeId, failure));
            if (taskError != null && taskError.isDone() && taskError.isCompletedExceptionally()) {
                LOGGER.warning("Discard chunk send from [{}], {}[{}] occur exception",
                        producerId, nodeId, abilityName);
                return;
            }
            if (!firstFrame || !vertex.isDone()) {
                LOGGER.warning("Discard chunk send from [{}], {}[{}] vertex is done",
                        producerId, nodeId, abilityName);
                return;
            }
            startStreamCall();
        }

        LOGGER.debug("Send chunk from [{}] to {}[{}]", producerId, nodeId, abilityName);
        StreamPayload payload = new StreamPayload(message, sourceAbility);
        for (StreamProcessor processor : processors.values()) {
            processor.receive(payload);
        }
    }

    /**
     * Returns generator iterators for a consumer ability.
     *
     * @param ability consumer ability
     * @param schema stream input schema
     * @param streamCallback optional callback invoked after each yielded chunk
     * @return generator map or empty map when the ability has no processor
     */
    public Map<String, Object> generator(
            ComponentAbility ability,
            Map<String, Object> schema,
            Consumer<Map<String, Object>> streamCallback) {
        StreamProcessor processor = processors.get(ability);
        if (processor == null) {
            return Map.of();
        }
        return processor.generator(schema, streamCallback);
    }

    /**
     * Waits for the stream call and processor tasks to complete.
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
     * Cancels the stream call and all processor tasks.
     */
    public synchronized void shutdown() {
        LOGGER.debug("Begin to shutdown stream actor task for {}", nodeId);
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
            LOGGER.debug("Succeed to shutdown stream actor task for {}", nodeId);
        } finally {
            task = null;
            taskCompletion = null;
            runningTasks.clear();
        }
    }

    private void startStreamCall() {
        CountDownLatch latch = new CountDownLatch(1);
        taskError = new CompletableFuture<>();
        taskCompletion = new CompletableFuture<>();
        task = VIRTUAL_EXECUTOR.submit(() -> {
            try {
                vertex.streamCall(latch, this::errorCallback);
                taskCompletion.complete(null);
            } catch (Throwable throwable) {
                taskCompletion.completeExceptionally(throwable);
                throw throwable;
            }
        });

        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }

        LOGGER.debug("Stream actor task node [{}] started", nodeId);
        for (Map.Entry<ComponentAbility, StreamProcessor> entry : processors.entrySet()) {
            ComponentAbility ability = entry.getKey();
            StreamProcessor processor = entry.getValue();
            CompletableFuture<Void> completion = new CompletableFuture<>();
            Future<?> processorTask = VIRTUAL_EXECUTOR.submit(() -> {
                try {
                    processor.run(ability);
                    completion.complete(null);
                } catch (Throwable throwable) {
                    completion.completeExceptionally(throwable);
                    throw throwable;
                }
            });
            runningTasks.add(new RunningTask(ability, processorTask, completion));
        }
    }

    private void errorCallback(Exception error) {
        if (error != null && taskError != null && !taskError.isDone()) {
            taskError.completeExceptionally(error);
        }
    }

    private static Optional<Throwable> taskFailure(Future<?> future) {
        if (future == null) {
            return Optional.empty();
        }
        try {
            future.get();
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.of(exception);
        } catch (ExecutionException exception) {
            return Optional.of(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            return Optional.of(exception);
        }
    }

    private void awaitCompletion(CompletableFuture<Void> completion, String taskName) {
        try {
            completion.get(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            LOGGER.warning("Timed out waiting for {} of node [{}] to stop", taskName, nodeId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Interrupted while waiting for {} of node [{}] to stop", taskName, nodeId);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            LOGGER.warning("Failed while waiting for {} of node [{}] to stop", taskName, nodeId, cause);
        }
    }

    private record RunningTask(
            ComponentAbility ability,
            Future<?> future,
            CompletableFuture<Void> completion) {
    }
}
