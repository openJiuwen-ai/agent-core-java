/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

/**
 * Thread-local holder for the currently executing session.
 * <p>
 * This mirrors Python's session context behaviour closely enough for tools and rails that need
 * access to the active session without receiving it explicitly.
 * 
 * @since 0.1.7
 */
public final class SessionContextHolder {
    private static final ThreadLocal<Session> CURRENT_SESSION = new ThreadLocal<Session>();

    /**
     * SessionContextHolder.
     * 
     * @since 0.1.7
     */
    private SessionContextHolder() {
    }

    /**
     * Return the session currently bound to the executing thread.
     * 
     * @return current session, or null when none is bound
     * @since 0.1.7
     */
    public static Session getCurrentSession() {
        return CURRENT_SESSION.get();
    }

    /**
     * Bind a session to the executing thread.
     * 
     * @param session session to bind
     * @since 0.1.7
     */
    public static void setCurrentSession(Session session) {
        if (session == null) {
            CURRENT_SESSION.remove();
            return;
        }
        CURRENT_SESSION.set(session);
    }

    /**
     * Clear the session bound to the executing thread.
     * 
     * @since 0.1.7
     */
    public static void clearCurrentSession() {
        CURRENT_SESSION.remove();
    }
}
