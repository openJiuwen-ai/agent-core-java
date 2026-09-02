/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

/**
 * Inheritable session context for agent-team spawn flows.
 * <p>
 * Mirrors Python's {@code spawn/context.py}: the current session id is stored in thread-local
 * context, can be set for child work, and can later be reset to the previous value.
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
            return;
        }
        if (token.previousSessionId() == null) {
            SESSION_ID_CONTEXT.remove();
            return;
        }
        SESSION_ID_CONTEXT.set(token.previousSessionId());
    }

    /**
     * Public record SessionToken used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record SessionToken(String previousSessionId) {
    }
}
