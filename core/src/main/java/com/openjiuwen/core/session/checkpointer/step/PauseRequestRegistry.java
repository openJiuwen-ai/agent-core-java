/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import com.openjiuwen.core.multitenant.TenantContextHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local registry of user pause intents, keyed by session id.
 *
 * <p>Registry keys carry the tenant prefix of the calling thread (same
 * composite form as the in-memory checkpointer's session keys), so same-named
 * sessions of different tenants never share one pause intent. The key is
 * resolved from the thread's tenant context on both the publishing and the
 * consuming side, so both sides must resolve the same context — the session's
 * tenant bound on both threads, or neither — matching the consistency the
 * tenant-aware checkpointer backends already impose on pause/resume flows.</p>
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
     *                  (operator, reason, timestamps), may be null; entries
     *                  with null values are dropped because the persisted
     *                  metadata map may not carry null values
     * @return true when the intent was newly published, false when a
     *         pending intent for the session already exists
     */
    public static boolean requestPause(String sessionId, Map<String, Object> pauseInfo) {
        if (isBlank(sessionId)) {
            return false;
        }
        return PAUSE_INTENTS.putIfAbsent(registryKey(sessionId), copyMetadata(pauseInfo)) == null;
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> pauseInfo) {
        if (pauseInfo == null || pauseInfo.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        pauseInfo.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Collections.unmodifiableMap(copy);
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
        return PAUSE_INTENTS.remove(registryKey(sessionId));
    }

    /**
     * Whether a pause intent is pending for one session without consuming it.
     *
     * @param sessionId id of the session to inspect
     * @return true when a pause intent is pending
     */
    public static boolean hasPauseRequest(String sessionId) {
        return !isBlank(sessionId) && PAUSE_INTENTS.containsKey(registryKey(sessionId));
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
            PAUSE_INTENTS.remove(registryKey(sessionId));
        }
    }

    /**
     * Build the registry key for one session: the tenant prefix of the
     * current thread followed by the session id, mirroring the composite
     * session key form of the tenant-aware checkpointer backends. Called
     * without a bound tenant it falls back to the bare session id, so the
     * tenant-less mode keeps its previous key shape.
     *
     * @param sessionId caller-provided session id, never null or blank
     * @return the tenant-scoped registry key
     */
    private static String registryKey(String sessionId) {
        var ctx = TenantContextHolder.getCurrentTenant();
        if (ctx != null && ctx.isTenantAware()) {
            return ctx.safeTenantId() + ":" + sessionId;
        }
        return sessionId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
