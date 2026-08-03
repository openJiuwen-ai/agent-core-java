/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

/**
 * Mirrors Python's session-scoped helpers in
 * {@code openjiuwen/harness/tools/worktree/session.py}.
 */
public final class WorktreeSessionContext {

    private static final InheritableThreadLocal<WorktreeSessionState> STATE = new InheritableThreadLocal<>();

    private WorktreeSessionContext() {
    }

    public static WorktreeSession getCurrentSession() {
        return getState().getSession();
    }

    public static void setCurrentSession(WorktreeSession session) {
        getState().setSession(session);
    }

    public static String getDefaultWorktreeName() {
        return getState().getDefaultWorktreeName();
    }

    public static void setDefaultWorktreeName(String name) {
        getState().setDefaultWorktreeName(name);
    }

    public static void initSessionState() {
        getState();
    }

    public static WorktreeSession requireCurrentSession() {
        WorktreeSession session = getCurrentSession();
        if (session == null) {
            throw new IllegalStateException("Not in a worktree session");
        }
        return session;
    }

    static WorktreeSessionState getState() {
        WorktreeSessionState state = STATE.get();
        if (state == null) {
            state = new WorktreeSessionState();
            STATE.set(state);
        }
        return state;
    }
}
