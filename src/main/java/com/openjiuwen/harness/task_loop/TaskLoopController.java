/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Public class TaskLoopController used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TaskLoopController {
    /**
     * DEFAULT_SESSION_ID.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_SESSION_ID = "__default__";

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final ConcurrentMap<String, SessionState> sessionStates = new ConcurrentHashMap<>();
    private final LoopQueues defaultQueues;

    /**
     * TaskLoopController.
     * 
     * @since 0.1.7
     */
    public TaskLoopController() {
        this(new LoopQueues());
    }

    /**
     * TaskLoopController.
     * 
     * @param defaultQueues defaultQueues
     * @since 0.1.7
     */
    public TaskLoopController(LoopQueues defaultQueues) {
        this.defaultQueues = defaultQueues == null ? new LoopQueues() : defaultQueues;
        sessionStates.put(DEFAULT_SESSION_ID, new SessionState(this.defaultQueues));
    }

    /**
     * prepareRound.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int prepareRound() {
        return prepareRound(DEFAULT_SESSION_ID, false);
    }

    /**
     * prepareRound.
     * 
     * @param sessionId sessionId
     * @param isFollowUp isFollowUp
     * @return the result
     * @since 0.1.7
     */
    public int prepareRound(String sessionId, boolean isFollowUp) {
        SessionState state = state(sessionId);
        int round = state.roundCounter.incrementAndGet();
        state.isLastRoundFollowUp = isFollowUp;
        state.isRoundActive = true;
        state.lastResult = null;
        return round;
    }

    /**
     * submitRound.
     * 
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    public int submitRound(String query) {
        return submitRound(DEFAULT_SESSION_ID, query, false);
    }

    /**
     * submitRound.
     * 
     * @param query query
     * @param isFollowUp isFollowUp
     * @return the result
     * @since 0.1.7
     */
    public int submitRound(String query, boolean isFollowUp) {
        return submitRound(DEFAULT_SESSION_ID, query, isFollowUp);
    }

    /**
     * submitRound.
     * 
     * @param sessionId sessionId
     * @param query query
     * @param isFollowUp isFollowUp
     * @return the result
     * @since 0.1.7
     */
    public int submitRound(String sessionId, String query, boolean isFollowUp) {
        return prepareRound(sessionId, isFollowUp);
    }

    /**
     * waitRoundCompletion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> waitRoundCompletion() {
        return waitRoundCompletion(DEFAULT_SESSION_ID);
    }

    /**
     * waitRoundCompletion.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> waitRoundCompletion(String sessionId) {
        SessionState state = state(sessionId);
        Map<String, Object> lastResult = state.lastResult;
        if (!state.isRoundActive && lastResult != null && !isSubmissionAck(lastResult)) {
            return lastResult;
        }
        if (!state.isRoundActive) {
            return Map.of("error", "no active round");
        }
        return Map.of("error", "completion_timeout");
    }

    /**
     * resolveCompletion.
     * 
     * @param completedRound completedRound
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> resolveCompletion(int completedRound, Map<String, Object> result) {
        return resolveCompletion(DEFAULT_SESSION_ID, completedRound, result);
    }

    /**
     * resolveCompletion.
     * 
     * @param sessionId sessionId
     * @param completedRound completedRound
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> resolveCompletion(String sessionId, int completedRound, Map<String, Object> result) {
        SessionState state = state(sessionId);
        int currentRound = state.roundCounter.get();
        if (completedRound != currentRound) {
            return Map.of("status", "stale", "round", completedRound, "current_round", currentRound);
        }
        state.lastResult = result == null ? Map.of("status", "completed") : Map.copyOf(result);
        state.isRoundActive = false;
        return state.lastResult;
    }

    /**
     * recordSubmission.
     * 
     * @param result result
     * @since 0.1.7
     */
    public void recordSubmission(Map<String, Object> result) {
        recordSubmission(DEFAULT_SESSION_ID, result);
    }

    /**
     * Record the handle_input submit ack for {@link #getLastResult()}.
     *
     * @param sessionId sessionId
     * @param result result
     * @since 0.1.7
     */
    public void recordSubmission(String sessionId, Map<String, Object> result) {
        if (result == null) {
            return;
        }
        SessionState state = state(sessionId);
        Map<String, Object> current = state.lastResult;
        if (current != null && !isSubmissionAck(current)) {
            return;
        }
        state.lastResult = Map.copyOf(result);
    }

    /**
     * abort.
     * 
     * @param reason reason
     * @since 0.1.7
     */
    public void abort(String reason) {
        abort(DEFAULT_SESSION_ID, reason);
    }

    /**
     * abort.
     * 
     * @param sessionId sessionId
     * @param reason reason
     * @since 0.1.7
     */
    public void abort(String sessionId, String reason) {
        SessionState state = state(sessionId);
        state.lastResult = Map.of("status", "aborted", "reason", reason == null ? "abort" : reason);
        state.isRoundActive = false;
    }

    /**
     * getLastResult.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getLastResult() {
        return getLastResult(DEFAULT_SESSION_ID);
    }

    /**
     * getLastResult.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getLastResult(String sessionId) {
        return state(sessionId).lastResult;
    }

    /**
     * enqueueFollowUp.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void enqueueFollowUp(String message) {
        enqueueFollowUp(DEFAULT_SESSION_ID, message);
    }

    /**
     * enqueueFollowUp.
     * 
     * @param sessionId sessionId
     * @param message message
     * @since 0.1.7
     */
    public void enqueueFollowUp(String sessionId, String message) {
        state(sessionId).queues.pushFollowUp(message);
    }

    /**
     * enqueueSteering.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void enqueueSteering(String message) {
        enqueueSteering(DEFAULT_SESSION_ID, message);
    }

    /**
     * enqueueSteering.
     * 
     * @param sessionId sessionId
     * @param message message
     * @since 0.1.7
     */
    public void enqueueSteering(String sessionId, String message) {
        state(sessionId).queues.pushSteer(message);
    }

    /**
     * hasFollowUp.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasFollowUp() {
        return hasFollowUp(DEFAULT_SESSION_ID);
    }

    /**
     * hasFollowUp.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public boolean hasFollowUp(String sessionId) {
        if (DEFAULT_SESSION_ID.equals(normalizeSessionId(sessionId))) {
            return state(DEFAULT_SESSION_ID).queues.hasFollowUp();
        }
        return state(sessionId).queues.hasFollowUp() || state(DEFAULT_SESSION_ID).queues.hasFollowUp();
    }

    /**
     * drainFollowUp.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> drainFollowUp() {
        return drainFollowUp(DEFAULT_SESSION_ID);
    }

    /**
     * drainFollowUp.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
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
     * drainSteering.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> drainSteering() {
        return drainSteering(DEFAULT_SESSION_ID);
    }

    /**
     * drainSteering.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public List<String> drainSteering(String sessionId) {
        return state(sessionId).queues.drainSteering();
    }

    /**
     * getInteractionQueues.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LoopQueues getInteractionQueues() {
        return getInteractionQueues(DEFAULT_SESSION_ID);
    }

    /**
     * getInteractionQueues.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public LoopQueues getInteractionQueues(String sessionId) {
        return state(sessionId).queues;
    }

    /**
     * getRoundCounter.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRoundCounter() {
        return getRoundCounter(DEFAULT_SESSION_ID);
    }

    /**
     * getRoundCounter.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public int getRoundCounter(String sessionId) {
        return state(sessionId).roundCounter.get();
    }

    /**
     * isRoundActive.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public boolean isRoundActive(String sessionId) {
        return state(sessionId).isRoundActive;
    }

    /**
     * clearSession.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void clearSession(String sessionId) {
        if (sessionId == null || DEFAULT_SESSION_ID.equals(sessionId)) {
            return;
        }
        sessionStates.remove(sessionId);
    }

    /**
     * Drop every session state except the default one.
     *
     * <p>Per-task DeepAgent instances accumulate one SessionState (queues,
     * round counters, last results) per session; without this cleanup the
     * states pin the agent graph after destroy.</p>
     *
     * @since 0.1.15
     */
    public void clearAllSessions() {
        sessionStates.keySet().removeIf(sessionId -> !DEFAULT_SESSION_ID.equals(sessionId));
    }

    /**
     * state.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    private SessionState state(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        return sessionStates.computeIfAbsent(normalized,
                ignored -> new SessionState(DEFAULT_SESSION_ID.equals(normalized) ? defaultQueues : new LoopQueues()));
    }

    /**
     * normalizeSessionId.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    private String normalizeSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? DEFAULT_SESSION_ID : sessionId;
    }

    private static boolean isSubmissionAck(Map<String, Object> result) {
        return "submitted".equals(result.get("status"));
    }

    private static final class SessionState {
        private final LoopQueues queues;
        private final AtomicInteger roundCounter = new AtomicInteger();
        private volatile boolean isLastRoundFollowUp;
        private volatile boolean isRoundActive;
        private volatile Map<String, Object> lastResult;

        /**
         * SessionState.
         * 
         * @param queues queues
         * @since 0.1.7
         */
        private SessionState(LoopQueues queues) {
            this.queues = queues;
        }
    }
}
