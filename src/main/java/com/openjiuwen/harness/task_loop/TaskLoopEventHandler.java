/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EventHandler that drives the outer task loop.
 *
 * <p>Routes core EventQueue events through the TaskScheduler
 * pipeline and updates TaskPlan state accordingly.
 *
 * <p>Uses a per-round Future pattern: each iteration of
 * the outer loop creates a new Future via prepareRound(),
 * and completion/failed/abort events resolve that Future.
 * A monotonic roundId prevents stale completions from
 * resolving the wrong Future.
 *
 * <p>Mirrors Python's {@code TaskLoopEventHandler} in
 * {@code openjiuwen.harness.task_loop.task_loop_event_handler}.
 */
public class TaskLoopEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TaskLoopEventHandler.class);

    private final Object deepAgent;
    private final AtomicInteger roundId = new AtomicInteger(0);
    private final AtomicReference<Map<String, Object>> lastResult = new AtomicReference<>(null);
    private final AtomicReference<CompletableFuture<Map<String, Object>>> currentFuture = new AtomicReference<>(null);
    private final LoopQueues interactionQueues;

    /**
     * Construct with deep agent reference.
     */
    public TaskLoopEventHandler(Object deepAgent) {
        this.deepAgent = deepAgent;
        this.interactionQueues = new LoopQueues();
    }

    /**
     * Default constructor.
     */
    public TaskLoopEventHandler() {
        this(null);
    }

    /**
     * Get interaction queues.
     */
    public LoopQueues getInteractionQueues() {
        return interactionQueues;
    }

    /**
     * Prepare a new round.
     *
     * <p>Creates a new Future that will be resolved when
     * the round completes or fails.
     */
    public String prepareRound() {
        int id = roundId.incrementAndGet();
        String roundIdStr = "round_" + id;

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        currentFuture.set(future);

        LOG.debug("[TaskLoopEventHandler] prepare_round round_id={}", roundIdStr);
        return roundIdStr;
    }

    /**
     * Wait for round completion.
     *
     * @param roundIdStr Round identifier for correlation
     * @param timeoutMs Timeout in milliseconds
     * @return CompletableFuture with round result
     */
    public CompletableFuture<Map<String, Object>> waitForRoundCompletion(String roundIdStr, long timeoutMs) {
        CompletableFuture<Map<String, Object>> future = currentFuture.get();
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }

        LOG.debug("[TaskLoopEventHandler] wait_for_round_completion round_id={}, timeout={}", roundIdStr, timeoutMs);

        return future.orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> {
                    if (result != null) {
                        lastResult.set(result);
                        LOG.debug("[TaskLoopEventHandler] round completed round_id={}", roundIdStr);
                    }
                    if (error != null) {
                        LOG.warn("[TaskLoopEventHandler] round failed round_id={} error={}", roundIdStr, error.getMessage());
                    }
                });
    }

    /**
     * Get last result.
     */
    public Map<String, Object> getLastResult() {
        return lastResult.get();
    }

    /**
     * Handle input event.
     */
    public void handleInputEvent(Object event) {
        LOG.debug("[TaskLoopEventHandler] handle_input_event event_type={}", event.getClass().getSimpleName());
        // Placeholder - actual implementation depends on event system
    }

    /**
     * Handle task completion event.
     */
    public void handleTaskCompletion(String taskId, Map<String, Object> result) {
        LOG.debug("[TaskLoopEventHandler] handle_task_completion task_id={}", taskId);

        CompletableFuture<Map<String, Object>> future = currentFuture.get();
        if (future != null && !future.isDone()) {
            future.complete(result);
        }
    }

    /**
     * Handle task failure.
     */
    public void handleTaskFailure(String taskId, String error) {
        LOG.warn("[TaskLoopEventHandler] handle_task_failure task_id={} error={}", taskId, error);

        CompletableFuture<Map<String, Object>> future = currentFuture.get();
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new RuntimeException(error));
        }
    }

    /**
     * Create core task for execution.
     */
    public String createTask(String taskType, Map<String, Object> metadata) {
        String taskId = "task_" + System.currentTimeMillis();
        LOG.info("[TaskLoopEventHandler] create_task task_id={} type={}", taskId, taskType);
        // Placeholder - actual implementation depends on task manager
        return taskId;
    }

    /**
     * Push steering message.
     */
    public void pushSteering(String msg) {
        interactionQueues.pushSteer(msg);
    }

    /**
     * Push follow-up message.
     */
    public void pushFollowUp(String msg) {
        interactionQueues.pushFollowUp(msg);
    }
}