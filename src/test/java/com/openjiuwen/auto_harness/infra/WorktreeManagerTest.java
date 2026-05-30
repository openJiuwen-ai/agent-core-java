/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.infra.worktree_manager}.
 * Tests for WorktreeManager base repo and worktree name generation.
 */
class WorktreeManagerTest {

    @Test
    void usesLocalRepoAsBaseRepo() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setLocalRepo(".");
        WorktreeManager manager = new WorktreeManager(config);
        String base = manager.baseRepo();
        org.junit.jupiter.api.Assertions.assertTrue(base.length() > 0);
    }

    @Test
    void worktreeNameUsesSlugifiedTopic() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        WorktreeManager manager = new WorktreeManager(config);
        assertEquals("Fix-bug-123", manager.worktreeNameFor("Fix bug #123"));
    }
}
