/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

/**
 * Common workspace directory node names.
 *
 * <p>Mirrors Python's {@code WorkspaceNode} in
 * {@code openjiuwen/harness/workspace/workspace.py}.</p>
 */
public enum WorkspaceNode {
    AGENT_MD("AGENT.md"),
    SOUL_MD("SOUL.md"),
    HEARTBEAT_MD("HEARTBEAT.md"),
    IDENTITY_MD("IDENTITY.md"),
    USER_MD("USER.md"),
    MEMORY("memory"),
    CODING_MEMORY("coding_memory"),
    TODO("todo"),
    MESSAGES("messages"),
    SKILLS("skills"),
    AGENTS("agents"),
    MEMORY_MD("MEMORY.md"),
    DAILY_MEMORY("daily_memory"),
    TEAM_LINKS(".team"),
    WORKTREE_LINKS(".worktree");

    private final String value;

    WorkspaceNode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
