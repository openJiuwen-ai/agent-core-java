/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorktreeNoticeTest {

    @Test
    void buildWorktreeNoticeIncludesBothContextsAndSafetyGuidance() {
        String notice = WorktreeNotice.buildWorktreeNotice("/repo/root", "/repo/.worktrees/feat-x");

        assertTrue(notice.contains("isolated git worktree at /repo/.worktrees/feat-x"));
        assertTrue(notice.contains("The parent context lives in /repo/root"));
        assertTrue(notice.contains("Paths from the parent context refer to /repo/root"));
        assertTrue(notice.contains("Translate them to your worktree root before use"));
        assertTrue(notice.contains("Your changes stay in this worktree and will not affect the parent"));
    }
}
