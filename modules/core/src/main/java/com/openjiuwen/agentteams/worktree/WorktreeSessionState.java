/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

/**
 * Thread-local holder for the active worktree session.
 * 
 * @since 0.1.7
 */
public final class WorktreeSessionState {
    private static final ThreadLocal<Holder> STATE = ThreadLocal.withInitial(Holder::new);

    /**
     * WorktreeSessionState.
     * 
     * @since 0.1.7
     */
    private WorktreeSessionState() {
    }

    /**
     * initSessionState.
     * 
     * @since 0.1.7
     */
    public static void initSessionState() {
        STATE.get();
    }

    /**
     * getCurrentSession.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static WorktreeSession getCurrentSession() {
        return STATE.get().session;
    }

    /**
     * setCurrentSession.
     * 
     * @param session session
     * @since 0.1.7
     */
    public static void setCurrentSession(WorktreeSession session) {
        STATE.get().session = session;
    }

    /**
     * requireCurrentSession.
     * 
     * @return the result
     * @since 0.1.7
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
