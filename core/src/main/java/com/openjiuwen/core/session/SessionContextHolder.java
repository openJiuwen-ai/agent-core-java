/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

/**
 * Thread-local holder for the currently executing session.
 */
public final class SessionContextHolder {

    private static final ThreadLocal<Object> CURRENT_SESSION = new ThreadLocal<>();

    private SessionContextHolder() {
    }

    public static Object getCurrentSession() {
        return CURRENT_SESSION.get();
    }

    public static <T> T getCurrentSession(Class<T> sessionType) {
        Object session = CURRENT_SESSION.get();
        if (session == null) {
            return null;
        }
        return sessionType.cast(session);
    }

    public static void setCurrentSession(Object session) {
        if (session == null) {
            CURRENT_SESSION.remove();
        } else {
            CURRENT_SESSION.set(session);
        }
    }

    public static void clearCurrentSession() {
        CURRENT_SESSION.remove();
    }

    public static void restoreCurrentSession(Object session) {
        setCurrentSession(session);
    }

    public static String resolveSessionId(Object session) {
        if (session instanceof AgentSessionApi agentSession) {
            return agentSession.getSessionId();
        }
        if (session instanceof BaseSession baseSession) {
            return baseSession.getSessionId();
        }
        if (session instanceof NodeSessionApi nodeSession) {
            return nodeSession.getSessionId();
        }
        return null;
    }
}
