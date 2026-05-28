/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * TaskLoopController — Controller for round-based loops.
 *
 * <p>Encapsulates round management (prepare/wait/complete),
 * follow-up queue operations, and loop exit logic that
 * are specific to the DeepAgent outer task loop.
 *
 * <p>Mirrors Python's {@code TaskLoopController} in
 * {@code openjiuwen.harness.task_loop.task_loop_controller}.
 */
public class TaskLoopController {

    private static final Logger LOG = LoggerFactory.getLogger(TaskLoopController.class);

    private Object eventHandler;
    private final LoopQueues interactionQueues;

    /**
     * Default constructor.
     */
    public TaskLoopController() {
        this.interactionQueues = new LoopQueues();
    }

    /**
     * Construct with event handler.
     */
    public TaskLoopController(Object eventHandler) {
        this.eventHandler = eventHandler;
        this.interactionQueues = new LoopQueues();
    }

    /**
     * Get interaction queues.
     */
    public LoopQueues getInteractionQueues() {
        return interactionQueues;
    }

    /**
     * Submit a round for execution.
     *
     * @param session Current session
     * @param query User query text
     * @param isFollowUp Whether this is a follow-up continuation
     * @return CompletableFuture for round submission
     */
    public CompletableFuture<Void> submitRound(Object session, String query, boolean isFollowUp) {
        return CompletableFuture.runAsync(() -> {
            String roundId = prepareRound();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("_handler_round_id", roundId);
            if (isFollowUp) {
                metadata.put("is_follow_up", true);
            }

            LOG.debug("[TaskLoopController] submit_round round_id={}, is_follow_up={}", roundId, isFollowUp);

            // Build and publish input event
            publishInputEvent(query, metadata);
        });
    }

    /**
     * Prepare a new round.
     */
    public String prepareRound() {
        String roundId = "round_" + System.currentTimeMillis();
        LOG.debug("[TaskLoopController] prepare_round round_id={}", roundId);
        return roundId;
    }

    /**
     * Wait for round completion.
     */
    public CompletableFuture<Boolean> waitForRoundCompletion(String roundId, long timeoutMs) {
        return CompletableFuture.supplyAsync(() -> {
            LOG.debug("[TaskLoopController] wait_for_round_completion round_id={}, timeout={}", roundId, timeoutMs);
            // Placeholder - actual implementation depends on event system
            return true;
        });
    }

    /**
     * Complete a round.
     */
    public void completeRound(String roundId) {
        LOG.debug("[TaskLoopController] complete_round round_id={}", roundId);
        // Placeholder - actual implementation depends on event system
    }

    /**
     * Publish input event.
     */
    private void publishInputEvent(String query, Map<String, Object> metadata) {
        LOG.debug("[TaskLoopController] publish_input_event query={}", query);
        // Placeholder - actual implementation depends on event system
    }

    /**
     * Get pending follow-up messages.
     */
    public List<String> getPendingFollowUps() {
        return interactionQueues.drainFollowUp();
    }

    /**
     * Get pending steering messages.
     */
    public List<String> getPendingSteering() {
        return interactionQueues.drainSteering();
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

    /**
     * Set event handler.
     */
    public void setEventHandler(Object eventHandler) {
        this.eventHandler = eventHandler;
    }
}