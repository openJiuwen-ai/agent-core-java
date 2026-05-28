/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

/**
 * Worktree context notice generation.
 * <p>
 * Builds informational text injected into the system prompt of spawned
 * members to inform them about their isolated worktree environment.
 * <p>
 * Mirrors Python's {@code notice} module in
 * {@code openjiuwen.agent_teams.worktree.notice}.
 */
public class WorktreeNotice {

    /**
     * Build context notice for agents running in a worktree.
     * <p>
     * Injected into the system prompt of spawned members to inform
     * them about the isolated environment.
     *
     * @param parentCwd    Working directory of the parent agent
     * @param worktreeCwd  Working directory of the worktree (isolated copy)
     * @return Multi-line notice string suitable for system prompt injection
     */
    public static String buildWorktreeNotice(String parentCwd, String worktreeCwd) {
        return String.format(
            "You are operating in an isolated git worktree at %s. " +
            "The parent agent works in %s — same repository, " +
            "same relative file structure, separate working copy.\n\n" +
            "Important:\n" +
            "- Paths from the parent context refer to %s\n" +
            "- Translate them to your worktree root before use\n" +
            "- Re-read files before editing if the parent may have modified them\n" +
            "- Your changes stay in this worktree and will not affect the parent",
            worktreeCwd, parentCwd, parentCwd
        );
    }
}