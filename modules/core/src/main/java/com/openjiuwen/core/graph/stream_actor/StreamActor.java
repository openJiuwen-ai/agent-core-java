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
import java.util.concurrent.locks.ReentrantLock;
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

    /**
     * Bounded wait for the stream task to actually start. The shared stream executor
     * may be busy under load; producers must never block forever on the start latch.
     *
     * @since 0.1.7
     */
    private static final long STREAM_START_TIMEOUT_MS = 30_000L;
    private static final ExecutorService STREAM_EXECUTOR =
            OpenJiuwenExecutors.newBoundedModulePool("stream-actor", false);

    /**
     * HashMap<>.
     *
     * @since 0.1.7
     */
    private final Map<ComponentAbility, StreamProcessor> processors = new HashMap<>();
    private final StreamConsumer vertex;
    private final String nodeId;

    /**
     * Guards the stream lifecycle state below ({@code task}, {@code taskCompletion},
     * {@code taskError}, {@code runningTasks}, {@code isStreamStartPending}).
     * <p>
     * The critical section is intentionally kept tiny: only state checks and task
     * submission. Waiting for the stream task to start and dispatching payloads to
     * processors happen OUTSIDE the lock so that one slow producer never serializes
     * the others.
     *
     * @since 0.1.7
     */
    private final ReentrantLock stateLock = new ReentrantLock();
    private volatile Future<?> task;
    private volatile CompletableFuture<Void> taskCompletion;
    private volatile CompletableFuture<Void> taskError;
    private volatile boolean isStreamStartPending;
    private boolean hasSeededCompletedSources;

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
     * @param isFirstFrame whether this is the first frame of a new stream
     * @param producerId the ID of the producer node
     * @since 0.1.7
     */
    public void send(Object message, ComponentAbility sourceAbility, boolean isFirstFrame,
            String producerId) {
        if (!vertex.shouldHandleMessage()) {
            logger.warning("Discard chunk send from [{}], {}[{}] unable to handle", producerId, nodeId,
                    sourceAbility.name());
            return;
        }

        // Fast, lock-held section: only inspect lifecycle state and submit the stream task.
        StartDecision decision = tryStartStreamTask(sourceAbility, isFirstFrame, producerId);

        // Wait for the stream task to start OUTSIDE the lock: the shared stream executor
        // may be busy, and holding the lock here would serialize every producer. A timeout
        // bounds the wait instead of blocking forever.
        awaitStreamTaskStart(decision.startLatch());

        // Start processors exactly once. Chunks buffered in processor queues meanwhile
        // (receive is a thread-safe offer) are consumed once the processors run.
        if (decision.shouldStartProcessors()) {
            startProcessors();
        }

        logger.debug("Send chunk from [{}] to {}[{}]", producerId, nodeId, sourceAbility.name());

        // Dispatch to all processors. StreamProcessor.receive is a thread-safe queue
        // offer, so this needs no lock.
        dispatchToProcessors(message, sourceAbility);
    }

    /**
     * Holder for the decision made inside the lock-held section of {@link #send}.
     *
     * @since 0.1.7
     */
    private record StartDecision(CountDownLatch startLatch, boolean shouldStartProcessors) {
        private static final StartDecision NONE = new StartDecision(null, false);
    }

    /**
     * Inspect lifecycle state under the lock and submit the stream task if needed.
     * Returns whether the caller should start processors afterwards.
     *
     * @param sourceAbility the ability that produced this message
     * @param isFirstFrame whether this is the first frame of a new stream
     * @param producerId the ID of the producer node
     * @return the start decision (start latch + whether to start processors)
     * @since 0.1.7
     */
    private StartDecision tryStartStreamTask(ComponentAbility sourceAbility, boolean isFirstFrame,
            String producerId) {
        CountDownLatch startLatch = null;
        boolean shouldStartProcessors = false;
        stateLock.lock();
        try {
            if (task == null || (task.isDone() && !isStreamStartPending)) {
                if (task != null && task.isDone() && taskFailure(task) != null) {
                    logger.warning("Exception occurred while sending chunk of node [{}]", nodeId, taskFailure(task));
                }
                if (taskError != null && taskError.isDone() && taskError.isCompletedExceptionally()) {
                    logger.warning("Discard chunk send from [{}], {}[{}] occur exception", producerId, nodeId,
                            sourceAbility.name());
                    return StartDecision.NONE;
                }
                if (!isFirstFrame || !vertex.isDone()) {
                    logger.warning("Discard chunk send from [{}], {}[{}] vertex is done", producerId, nodeId,
                            sourceAbility.name());
                    return StartDecision.NONE;
                }

                // Start stream call on the vertex
                CountDownLatch latch = new CountDownLatch(1);
                taskError = new CompletableFuture<>();
                taskCompletion = new CompletableFuture<>();
                isStreamStartPending = true;
                task = STREAM_EXECUTOR.submit(() -> {
                    try {
                        vertex.streamCall(latch, this::errorCallback);
                    } finally {
                        taskCompletion.complete(null);
                    }
                });
                startLatch = latch;
                shouldStartProcessors = true;
            }
        } finally {
            stateLock.unlock();
        }
        return new StartDecision(startLatch, shouldStartProcessors);
    }

    /**
     * Wait for the stream task to actually start, bounded by STREAM_START_TIMEOUT_MS.
     *
     * @param startLatch the latch returned by tryStartStreamTask (may be null)
     * @since 0.1.7
     */
    private void awaitStreamTaskStart(CountDownLatch startLatch) {
        if (startLatch == null) {
            return;
        }
        try {
            if (!startLatch.await(STREAM_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                logger.warning("Timed out waiting for stream task of node [{}] to start within {}ms", nodeId,
                        STREAM_START_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Interrupted while waiting for stream task of node [{}] to start", nodeId);
        }
    }

    /**
     * Dispatch a stream message to all processors. Thread-safe (receive is a queue offer).
     *
     * @param message the stream message
     * @param sourceAbility the ability that produced this message
     * @since 0.1.7
     */
    private void dispatchToProcessors(Object message, ComponentAbility sourceAbility) {
        StreamPayload payload = new StreamPayload(message, sourceAbility);
        for (StreamProcessor processor : processors.values()) {
            processor.receive(payload);
        }
    }

    /**
     * Start the processor tasks for the current stream, exactly once.
     *
     * @since 0.1.7
     */
    private void startProcessors() {
        stateLock.lock();
        try {
            if (!isStreamStartPending) {
                return;
            }
            isStreamStartPending = false;
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
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Seed source completions restored from a previous interrupted invocation.
     *
     * @param completedSources completed producer-ability keys
     * @since 0.1.7
     */
    public void seedCompletedSources(Set<String> completedSources) {
        if (completedSources == null || completedSources.isEmpty()) {
            return;
        }
        stateLock.lock();
        try {
            if (hasSeededCompletedSources) {
                return;
            }
            for (StreamProcessor processor : processors.values()) {
                processor.seedCompletedSources(completedSources);
            }
            hasSeededCompletedSources = true;
        } finally {
            stateLock.unlock();
        }
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
    public void awaitCompletion() {
        CompletableFuture<Void> taskComp;
        List<RunningTask> tasks;
        stateLock.lock();
        try {
            taskComp = taskCompletion;
            tasks = new ArrayList<>(runningTasks);
        } finally {
            stateLock.unlock();
        }

        // Wait outside the lock so concurrent sends are never blocked during teardown.
        if (taskComp != null) {
            awaitCompletion(taskComp, "stream actor task");
        }
        for (RunningTask runningTask : tasks) {
            awaitCompletion(runningTask.completion(), "stream actor processor " + runningTask.ability().name());
        }
    }

    /**
     * Shutdown the stream actor, cancelling all running tasks.
     *
     * @since 0.1.7
     */
    public void shutdown() {
        logger.debug("Begin to shutdown stream actor task for {}", nodeId);
        try {
            Future<?> currentTask;
            CompletableFuture<Void> currentTaskCompletion;
            List<RunningTask> tasks;
            stateLock.lock();
            try {
                currentTask = task;
                currentTaskCompletion = taskCompletion;
                tasks = new ArrayList<>(runningTasks);
                if (currentTask != null && !currentTask.isDone() && !currentTask.isCancelled()) {
                    currentTask.cancel(true);
                }
                if (taskError != null && !taskError.isDone() && !taskError.isCancelled()) {
                    taskError.cancel(true);
                }
                for (RunningTask runningTask : tasks) {
                    Future<?> future = runningTask.future();
                    if (!future.isDone() && !future.isCancelled()) {
                        future.cancel(true);
                    }
                }
            } finally {
                stateLock.unlock();
            }

            if (currentTaskCompletion != null) {
                awaitCompletion(currentTaskCompletion, "stream actor task");
            }
            for (RunningTask runningTask : tasks) {
                awaitCompletion(runningTask.completion(), "stream actor processor " + runningTask.ability().name());
            }
            logger.debug("Succeed to shutdown stream actor task for {}", nodeId);
        } finally {
            stateLock.lock();
            try {
                task = null;
                taskCompletion = null;
                taskError = null;
                isStreamStartPending = false;
                runningTasks.clear();
            } finally {
                stateLock.unlock();
            }
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
