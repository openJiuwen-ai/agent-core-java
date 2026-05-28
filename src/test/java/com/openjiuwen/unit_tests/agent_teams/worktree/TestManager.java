/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for worktree manager module.
 */
class TestManager {

    @Test
    @Tag("level0")
    void testWorktreeManagerClassExists() {
        assertNotNull(com.openjiuwen.agent_teams.worktree.WorktreeManager.class);
    }

    @Test
    @Tag("level0")
    void testWorktreeConfigClassExists() {
        assertNotNull(com.openjiuwen.agent_teams.worktree.WorktreeConfig.class);
    }

    @Test
    @Tag("level0")
    void testWorktreeSessionClassExists() {
        assertNotNull(com.openjiuwen.agent_teams.worktree.WorktreeSession.class);
    }
}