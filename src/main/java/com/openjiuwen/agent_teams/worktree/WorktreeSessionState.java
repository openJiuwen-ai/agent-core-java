/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

/**
 * Thread-local holder for the active worktree session.
 */
public final class WorktreeSessionState {
    private static final ThreadLocal<Holder> STATE = ThreadLocal.withInitial(Holder::new);

    private WorktreeSessionState() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void initSessionState() {
        STATE.get();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static WorktreeSession getCurrentSession() {
        return STATE.get().session;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void setCurrentSession(WorktreeSession session) {
        STATE.get().session = session;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static WorktreeSession requireCurrentSession() {
        WorktreeSession session = getCurrentSession();
        if (session == null) {
            throw new IllegalStateException("Not in a worktree session");
        }
        return session;
    }

    private static final class Holder {
        private WorktreeSession session;
    }
}
