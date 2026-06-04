/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

/**
 * Thread-local holder for the active worktree session.
 *
 * <p>Mirrors Python's ContextVar-based holder in
 * {@code openjiuwen.agent_teams.worktree.session}.</p>
 */
public final class WorktreeSessionHolder {

    private static final InheritableThreadLocal<SessionState> CURRENT = new InheritableThreadLocal<>() {
        @Override
        protected SessionState childValue(SessionState parentValue) {
            return parentValue;
        }
    };

    private WorktreeSessionHolder() {
    }

    public static WorktreeSession getCurrentSession() {
        return getState().session;
    }

    public static void setCurrentSession(WorktreeSession session) {
        getState().session = session;
    }

    public static void initSessionState() {
        getState();
    }

    public static WorktreeSession requireCurrentSession() {
        WorktreeSession session = getState().session;
        if (session == null) {
            throw new IllegalStateException("Not in a worktree session");
        }
        return session;
    }

    private static SessionState getState() {
        SessionState state = CURRENT.get();
        if (state == null) {
            state = new SessionState();
            CURRENT.set(state);
        }
        return state;
    }

    private static final class SessionState {
        private WorktreeSession session;
    }
}
