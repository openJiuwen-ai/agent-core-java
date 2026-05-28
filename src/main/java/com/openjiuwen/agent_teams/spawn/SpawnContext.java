/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

/**
 * Context Module for Agent Teams.
 * <p>
 * Provides context variable management for team isolation.
 * Uses ThreadLocal to support async environments with proper context isolation.
 * <p>
 * Mirrors Python's {@code context.py} in
 * {@code openjiuwen.agent_teams.spawn.context}.
 */
public final class SpawnContext {
    
    /** Thread-local context variable for session_id (used for message/topic isolation) */
    private static final ThreadLocal<String> SESSION_ID_CONTEXT = ThreadLocal.withInitial(() -> null);
    
    private SpawnContext() {
        // Utility class
    }
    
    /**
     * Set the current session_id context.
     * <p>
     * Mirrors Python: set_session_id(session_id)
     *
     * @param sessionId Session identifier to set as current context
     */
    public static void setSessionId(String sessionId) {
        SESSION_ID_CONTEXT.set(sessionId);
    }
    
    /**
     * Get the current session_id from context.
     * <p>
     * Mirrors Python: get_session_id()
     *
     * @return Current session_id or empty string if not set
     */
    public static String getSessionId() {
        String sessionId = SESSION_ID_CONTEXT.get();
        return sessionId != null ? sessionId : "";
    }
    
    /**
     * Reset session_id context to null.
     * <p>
     * Mirrors Python: reset_session_id(token)
     */
    public static void resetSessionId() {
        SESSION_ID_CONTEXT.remove();
    }
    
    /**
     * Execute code with a temporary session_id context.
     * <p>
     * Sets the session_id, executes the runnable, then resets.
     *
     * @param sessionId Session identifier
     * @param runnable Code to execute with this session context
     */
    public static void withSessionId(String sessionId, Runnable runnable) {
        String previous = SESSION_ID_CONTEXT.get();
        try {
            SESSION_ID_CONTEXT.set(sessionId);
            runnable.run();
        } finally {
            if (previous != null) {
                SESSION_ID_CONTEXT.set(previous);
            } else {
                SESSION_ID_CONTEXT.remove();
            }
        }
    }
}