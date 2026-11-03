/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import java.nio.file.Path;

/**
 * Builds context notice injected into spawned members' system prompt
 * when operating inside an isolated git worktree.
 * <p>
 * Mirrors Python worktree/notice.py.
 * </p>
 * 
 * @since 0.1.7
 */
public final class WorktreeNotice {
    /**
     * WorktreeNotice.
     * 
     * @since 0.1.7
     */
    private WorktreeNotice() {
    }

    /**
     * buildWorktreeNotice.
     * 
     * @param parentCwd parentCwd
     * @param worktreeCwd worktreeCwd
     * @return the result
     * @since 0.1.7
     */
    public static String buildWorktreeNotice(Path parentCwd, Path worktreeCwd) {
        String parentPath = parentCwd != null && parentCwd.toString() != null ? parentCwd.toString() : "/";
        String worktreePath = worktreeCwd != null && worktreeCwd.toString() != null ? worktreeCwd.toString() : "/";

        return "## Git Worktree Isolation Notice\n\n" + "You are operating inside an **isolated Git worktree**:\n\n"
                + "- **Your worktree root**: `" + worktreePath + "`\n" + "- **Parent repository root**: `" + parentPath
                + "`\n\n" + "**Guidelines**:\n" + "1. When you encounter paths that reference the parent location (`"
                + parentPath + "`), translate them to your worktree root (`" + worktreePath + "`).\n"
                + "2. Before editing a file, re-read it — the working tree may differ "
                + "from the original repository state.\n"
                + "3. Your changes stay isolated to this worktree until explicitly "
                + "merged back (typically handled by the team leader).\n"
                + "4. Use `EnterWorktreeTool` and `ExitWorktreeTool` to manage " + "worktree sessions if needed.\n";
    }
}
