/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import com.openjiuwen.core.common.logging.LoggingUtils;

/**
 * Inheritable session context for agent-team spawn flows.
 * <p>
 * Mirrors Python's {@code spawn/context.py} and {@code agent_teams.context.set_session_id}:
 * the current session id is stored in thread-local context and mirrored into
 * {@link LoggingUtils} so structured logs render the real session id instead of
 * {@code default_trace_id}.
 * </p>
 *
 * @since 0.1.7
 */
public final class SpawnContext {
    private static final InheritableThreadLocal<String> SESSION_ID_CONTEXT = new InheritableThreadLocal<>();

    /**
     * SpawnContext.
     *
     * @since 0.1.7
     */
    private SpawnContext() {
    }

    /**
     * setSessionId.
     *
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public static SessionToken setSessionId(String sessionId) {
        String previous = SESSION_ID_CONTEXT.get();
        SESSION_ID_CONTEXT.set(sessionId);
        LoggingUtils.setSessionId(sessionId == null || sessionId.isBlank() ? null : sessionId);
        return new SessionToken(previous);
    }

    /**
     * getSessionId.
     *
     * @return the result
     * @since 0.1.7
     */
    public static String getSessionId() {
        String sessionId = SESSION_ID_CONTEXT.get();
        return sessionId == null ? "" : sessionId;
    }

    /**
     * resetSessionId.
     *
     * @param token token
     * @since 0.1.7
     */
    public static void resetSessionId(SessionToken token) {
        if (token == null) {
            SESSION_ID_CONTEXT.remove();
            LoggingUtils.setSessionId();
            return;
        }
        if (token.previousSessionId() == null) {
            SESSION_ID_CONTEXT.remove();
            LoggingUtils.setSessionId();
            return;
        }
        SESSION_ID_CONTEXT.set(token.previousSessionId());
        LoggingUtils.setSessionId(token.previousSessionId());
    }

    /**
     * Public record SessionToken used by the Java parity implementation.
     *
     * @since 0.1.7
     */
    public record SessionToken(String previousSessionId) {
    }
}
