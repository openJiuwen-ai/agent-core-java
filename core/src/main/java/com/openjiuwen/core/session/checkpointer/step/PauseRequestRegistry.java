/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local registry of user pause intents, keyed by session id.
 *
 * <p>A pause request published here is observed by the agent execution loops
 * at their next step boundary (ReAct iteration head, DeepAgent task-loop
 * round tail); the observing loop then persists the PAUSED step cursor
 * through {@code Checkpointer#stepPaused} and stops with the
 * {@code PAUSED} termination reason. The registry is the in-process intent
 * channel; durable pause evidence lives only in the checkpointer state.</p>
 *
 * <p>The runtime publishes intents through the handler SPI, mirroring the
 * cancel-flag pattern of {@code ReActAgent.CANCEL_FLAGS}.</p>
 *
 * @since 0.1.17
 */
public final class PauseRequestRegistry {
    private static final Map<String, Map<String, Object>> PAUSE_INTENTS = new ConcurrentHashMap<>();

    private PauseRequestRegistry() {
    }

    /**
     * Publish a pause intent for one session; the executing loop consumes it
     * at the next step boundary.
     *
     * @param sessionId id of the session to pause, never null or blank
     * @param pauseInfo pause metadata persisted with the paused cursor
     *                  (operator, reason, timestamps), may be null
     * @return true when the intent was newly published, false when a
     *         pending intent for the session already exists
     */
    public static boolean requestPause(String sessionId, Map<String, Object> pauseInfo) {
        if (isBlank(sessionId)) {
            return false;
        }
        return PAUSE_INTENTS.putIfAbsent(sessionId, pauseInfo == null ? Map.of() : Map.copyOf(pauseInfo)) == null;
    }

    /**
     * Consume the pending pause intent of one session: the intent is removed
     * so a later execution is not paused again by a stale request.
     *
     * @param sessionId id of the session executing the next boundary
     * @return the pending pause metadata, or null when no pause was requested
     */
    public static Map<String, Object> consumePauseRequest(String sessionId) {
        if (isBlank(sessionId)) {
            return null;
        }
        return PAUSE_INTENTS.remove(sessionId);
    }

    /**
     * Whether a pause intent is pending for one session without consuming it.
     *
     * @param sessionId id of the session to inspect
     * @return true when a pause intent is pending
     */
    public static boolean hasPauseRequest(String sessionId) {
        return !isBlank(sessionId) && PAUSE_INTENTS.containsKey(sessionId);
    }

    /**
     * Discard the pending pause intent of one session without executing it,
     * used when the session is cleared or terminated before the intent is
     * consumed.
     *
     * @param sessionId id of the session whose intent is discarded
     */
    public static void clearPauseRequest(String sessionId) {
        if (!isBlank(sessionId)) {
            PAUSE_INTENTS.remove(sessionId);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
