/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

/**
 * Mirrors Python's notice helper in
 * {@code openjiuwen/harness/tools/worktree/notice.py}.
 */
public final class WorktreeNotice {

    private WorktreeNotice() {
    }

    public static String buildWorktreeNotice(String parentCwd, String worktreeCwd) {
        return "You are operating in an isolated git worktree at " + worktreeCwd + ". "
                + "The parent context lives in " + parentCwd + " — same repository, "
                + "same relative file structure, separate working copy.\n\n"
                + "Important:\n"
                + "- Paths from the parent context refer to " + parentCwd + "\n"
                + "- Translate them to your worktree root before use\n"
                + "- Re-read files before editing if the parent may have modified them\n"
                + "- Your changes stay in this worktree and will not affect the parent";
    }
}
