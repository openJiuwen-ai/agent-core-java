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

    private static final ThreadLocal<WorktreeSession> CURRENT = new ThreadLocal<>();

    private WorktreeSessionHolder() {
    }

    public static WorktreeSession getCurrentSession() {
        return CURRENT.get();
    }

    public static void setCurrentSession(WorktreeSession session) {
        CURRENT.set(session);
    }

    public static void initSessionState() {
        if (CURRENT.get() == null) {
            CURRENT.set(null);
        }
    }

    public static WorktreeSession requireCurrentSession() {
        WorktreeSession session = CURRENT.get();
        if (session == null) {
            throw new IllegalStateException("No active worktree session");
        }
        return session;
    }
}
