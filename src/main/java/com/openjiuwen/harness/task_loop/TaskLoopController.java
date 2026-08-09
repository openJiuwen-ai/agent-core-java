/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.session.AgentSessionApi;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Controller facade for DeepAgent outer task-loop rounds.
 *
 * <p>Mirrors Python's {@code TaskLoopController} in
 * {@code openjiuwen/harness/task_loop/task_loop_controller.py}.</p>
 */
public class TaskLoopController extends Controller {

    /** Default session id used when no explicit session is available. */
    public static final String DEFAULT_SESSION_ID = "default";

    private final LoopQueues interactionQueues = new LoopQueues();
    private CompletableFuture<Map<String, Object>> roundCompletion = new CompletableFuture<>();

    public LoopQueues getInteractionQueues() {
        return interactionQueues;
    }

    public CompletableFuture<Map<String, Object>> submitRound(
            AgentSessionApi session,
            String query,
            boolean isFollowUp,
            String runKind,
            Map<String, Object> runContext
    ) {
        roundCompletion = new CompletableFuture<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", session == null ? null : session.getSessionId());
        payload.put("query", query);
        payload.put("is_follow_up", isFollowUp);
        payload.put("run_kind", runKind);
        payload.put("run_context", runContext == null ? Map.of() : new LinkedHashMap<>(runContext));
        interactionQueues.input().add(payload);
        return roundCompletion;
    }

    public Map<String, Object> waitRoundCompletion(Duration timeout) {
        try {
            if (timeout == null) {
                return roundCompletion.get();
            }
            return roundCompletion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            return Map.of("status", "timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Map.of("status", "interrupted");
        } catch (Exception exception) {
            return Map.of("status", "failed", "error", exception.getMessage());
        }
    }

    public void completeRound(Map<String, Object> result) {
        roundCompletion.complete(result == null ? Map.of("status", "completed") : new LinkedHashMap<>(result));
    }

    public List<String> drainFollowUp() {
        return interactionQueues.drainFollowUp();
    }

    public List<String> drainSteering() {
        return interactionQueues.drainSteering();
    }

    /**
     * Drain steering messages for a given session.
     *
     * @param sessionId the session id (ignored in this implementation)
     * @return list of steering messages
     */
    public List<String> drainSteering(String sessionId) {
        return drainSteering();
    }

    /**
     * Drain follow-up messages for a given session.
     *
     * @param sessionId the session id (ignored in this implementation)
     * @return list of follow-up messages
     */
    public List<String> drainFollowUp(String sessionId) {
        return drainFollowUp();
    }

    /**
     * Get interaction queues for a given session.
     *
     * @param sessionId the session id (ignored in this implementation)
     * @return the interaction queues
     */
    public LoopQueues getInteractionQueues(String sessionId) {
        return getInteractionQueues();
    }

    /**
     * Get the round counter for a given session.
     *
     * @param sessionId the session id
     * @return the current round count
     */
    public int getRoundCounter(String sessionId) {
        return 0;
    }

    public void enqueueFollowUp(String message) {
        if (message != null && !message.isBlank()) {
            interactionQueues.pushFollowUp(message);
        }
    }

    /**
     * Enqueue a follow-up message with an associated session id.
     *
     * @param sessionId the session id (ignored in this implementation)
     * @param message   the follow-up message
     */
    public void enqueueFollowUp(String sessionId, String message) {
        enqueueFollowUp(message);
    }

    /**
     * Enqueue a steering message.
     *
     * @param sessionId the session id (ignored in this implementation)
     * @param message   the steering message
     */
    public void enqueueSteering(String sessionId, String message) {
        if (message != null && !message.isBlank()) {
            interactionQueues.pushSteer(message);
        }
    }

    public boolean hasFollowUp() {
        return interactionQueues.hasFollowUp();
    }
}
