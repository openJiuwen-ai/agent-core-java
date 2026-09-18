/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

/**
 * Mirrors Python's {@code WorktreeSessionState} in
 * {@code openjiuwen/harness/tools/worktree/session.py}.
 */
public class WorktreeSessionState {

    private WorktreeSession session;
    private String defaultWorktreeName;

    public WorktreeSession getSession() {
        return session;
    }

    public void setSession(WorktreeSession session) {
        this.session = session;
    }

    public String getDefaultWorktreeName() {
        return defaultWorktreeName;
    }

    public void setDefaultWorktreeName(String defaultWorktreeName) {
        this.defaultWorktreeName = defaultWorktreeName;
    }
}
