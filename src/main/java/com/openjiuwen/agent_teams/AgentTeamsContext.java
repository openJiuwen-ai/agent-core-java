/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

/**
 * Session context helpers for agent-team isolation.
 * <p>
 * Mirrors Python's module functions in
 * {@code openjiuwen/agent_teams/context.py}.
 */
public final class AgentTeamsContext {

    private static final ThreadLocal<String> SESSION_ID_CONTEXT = new InheritableThreadLocal<>();

    private AgentTeamsContext() {
    }

    /**
     * Token used to restore the prior session state.
     */
    public record SessionIdToken(String previousValue, boolean hadValue) {
    }

    public static SessionIdToken setSessionId(String sessionId) {
        String previous = SESSION_ID_CONTEXT.get();
        boolean hadValue = previous != null;
        SESSION_ID_CONTEXT.set(sessionId);
        return new SessionIdToken(previous, hadValue);
    }

    public static String getSessionId() {
        String sessionId = SESSION_ID_CONTEXT.get();
        return sessionId == null ? "" : sessionId;
    }

    public static void resetSessionId(SessionIdToken token) {
        if (token == null || !token.hadValue()) {
            SESSION_ID_CONTEXT.remove();
            return;
        }
        SESSION_ID_CONTEXT.set(token.previousValue());
    }
}
