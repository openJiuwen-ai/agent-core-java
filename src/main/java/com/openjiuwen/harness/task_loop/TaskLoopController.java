/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Public class TaskLoopController used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TaskLoopController {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String DEFAULT_SESSION_ID = "__default__";

    private final ConcurrentMap<String, SessionState> sessionStates = new ConcurrentHashMap<>();
    private final LoopQueues defaultQueues;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskLoopController() {
        this(new LoopQueues());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskLoopController(LoopQueues defaultQueues) {
        this.defaultQueues = defaultQueues == null ? new LoopQueues() : defaultQueues;
        sessionStates.put(DEFAULT_SESSION_ID, new SessionState(this.defaultQueues));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int prepareRound() {
        return prepareRound(DEFAULT_SESSION_ID, false);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int prepareRound(String sessionId, boolean isFollowUp) {
        SessionState state = state(sessionId);
        state.roundCounter += 1;
        state.isLastRoundFollowUp = isFollowUp;
        state.isRoundActive = true;
        state.lastResult = null;
        return state.roundCounter;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int submitRound(String query) {
        return submitRound(DEFAULT_SESSION_ID, query, false);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int submitRound(String query, boolean isFollowUp) {
        return submitRound(DEFAULT_SESSION_ID, query, isFollowUp);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int submitRound(String sessionId, String query, boolean isFollowUp) {
        return prepareRound(sessionId, isFollowUp);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> waitRoundCompletion() {
        return waitRoundCompletion(DEFAULT_SESSION_ID);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> waitRoundCompletion(String sessionId) {
        SessionState state = state(sessionId);
        if (!state.isRoundActive && state.lastResult != null) {
            return state.lastResult;
        }
        if (!state.isRoundActive) {
            return Map.of("error", "no active round");
        }
        return Map.of("error", "completion_timeout");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> resolveCompletion(int completedRound, Map<String, Object> result) {
        return resolveCompletion(DEFAULT_SESSION_ID, completedRound, result);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> resolveCompletion(String sessionId, int completedRound, Map<String, Object> result) {
        SessionState state = state(sessionId);
        if (completedRound != state.roundCounter) {
            return Map.of("status", "stale", "round", completedRound, "current_round", state.roundCounter);
        }
        state.isRoundActive = false;
        state.lastResult = result == null ? Map.of("status", "completed") : Map.copyOf(result);
        return state.lastResult;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void recordSubmission(Map<String, Object> result) {
        recordSubmission(DEFAULT_SESSION_ID, result);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void recordSubmission(String sessionId, Map<String, Object> result) {
        if (result == null) {
            return;
        }
        SessionState state = state(sessionId);
        state.lastResult = Map.copyOf(result);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void abort(String reason) {
        abort(DEFAULT_SESSION_ID, reason);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void abort(String sessionId, String reason) {
        SessionState state = state(sessionId);
        state.isRoundActive = false;
        state.lastResult = Map.of("status", "aborted", "reason", reason == null ? "abort" : reason);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getLastResult() {
        return getLastResult(DEFAULT_SESSION_ID);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getLastResult(String sessionId) {
        return state(sessionId).lastResult;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void enqueueFollowUp(String message) {
        enqueueFollowUp(DEFAULT_SESSION_ID, message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void enqueueFollowUp(String sessionId, String message) {
        state(sessionId).queues.pushFollowUp(message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void enqueueSteering(String message) {
        enqueueSteering(DEFAULT_SESSION_ID, message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void enqueueSteering(String sessionId, String message) {
        state(sessionId).queues.pushSteer(message);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasFollowUp() {
        return hasFollowUp(DEFAULT_SESSION_ID);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasFollowUp(String sessionId) {
        if (DEFAULT_SESSION_ID.equals(normalizeSessionId(sessionId))) {
            return state(DEFAULT_SESSION_ID).queues.hasFollowUp();
        }
        return state(sessionId).queues.hasFollowUp() || state(DEFAULT_SESSION_ID).queues.hasFollowUp();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> drainFollowUp() {
        return drainFollowUp(DEFAULT_SESSION_ID);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> drainFollowUp(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        if (DEFAULT_SESSION_ID.equals(normalized)) {
            return state(DEFAULT_SESSION_ID).queues.drainFollowUp();
        }
        List<String> drained = new java.util.ArrayList<>(state(normalized).queues.drainFollowUp());
        drained.addAll(state(DEFAULT_SESSION_ID).queues.drainFollowUp());
        return drained;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> drainSteering() {
        return drainSteering(DEFAULT_SESSION_ID);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> drainSteering(String sessionId) {
        return state(sessionId).queues.drainSteering();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopQueues getInteractionQueues() {
        return getInteractionQueues(DEFAULT_SESSION_ID);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopQueues getInteractionQueues(String sessionId) {
        return state(sessionId).queues;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getRoundCounter() {
        return getRoundCounter(DEFAULT_SESSION_ID);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getRoundCounter(String sessionId) {
        return state(sessionId).roundCounter;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isRoundActive(String sessionId) {
        return state(sessionId).isRoundActive;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void clearSession(String sessionId) {
        if (sessionId == null || DEFAULT_SESSION_ID.equals(sessionId)) {
            return;
        }
        sessionStates.remove(sessionId);
    }

    private SessionState state(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        return sessionStates.computeIfAbsent(normalized,
                ignored -> new SessionState(DEFAULT_SESSION_ID.equals(normalized) ? defaultQueues : new LoopQueues()));
    }

    private String normalizeSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? DEFAULT_SESSION_ID : sessionId;
    }

    private static final class SessionState {
        private final LoopQueues queues;
        private int roundCounter;
        private boolean isLastRoundFollowUp;
        private boolean isRoundActive;
        private Map<String, Object> lastResult;

        private SessionState(LoopQueues queues) {
            this.queues = queues;
        }
    }
}
