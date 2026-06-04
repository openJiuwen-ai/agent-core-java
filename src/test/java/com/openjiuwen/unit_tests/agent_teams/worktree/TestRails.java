/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.WorktreeRails;
import com.openjiuwen.agent_teams.worktree.WorktreeSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for worktree rails.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.worktree.rails}.</p>
 */
class TestRails {

    @Test
    @Tag("level1")
    void baseHooksMatchPythonNoOpDefaults(@TempDir Path tempDir) {
        WorktreeRails rails = new WorktreeRails();
        WorktreeSession session = new WorktreeSession("/repo", tempDir.toString(), "teammate-abcd");
        List<String> files = List.of("a.txt", "b.txt");

        assertNull(rails.beforeWorktreeCreate(null, "teammate-abcd", "/repo"));
        assertDoesNotThrow(() -> rails.afterWorktreeCreate(null, session).join());
        assertNull(rails.beforeWorktreeExit(null, session, "keep"));
        assertDoesNotThrow(() -> rails.afterWorktreeExit(null, session, "keep").join());
        assertTrue(rails.onWorktreeFileWrite(null, session, tempDir.resolve("a.txt").toString()));
        assertNull(rails.beforeWorktreeCommit(null, session, "message", files));
        assertDoesNotThrow(() -> rails.afterWorktreeCommit(null, session, "abc123").join());
        assertSame(files, rails.onWorktreeSync(null, session, "push", files));

        assertEquals("content", rails.beforeFileWrite("a.txt", "content"));
        assertEquals("message", rails.beforeCommit("message"));
        assertTrue(rails.filterSyncFile("a.txt", "content"));
    }

    @Test
    @Tag("level1")
    void autoSetupDetectsPythonProject(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname = \"demo\"\n");

        assertEquals(List.of("uv sync --quiet"), WorktreeRails.AutoSetupRail.detectSetup(tempDir.toString()));
    }

    @Test
    @Tag("level1")
    void autoSetupDetectsNodeProject(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("package.json"), "{\"name\":\"demo\"}\n");

        assertEquals(List.of("npm install --silent"), WorktreeRails.AutoSetupRail.detectSetup(tempDir.toString()));
    }

    @Test
    @Tag("level1")
    void diffSummaryDoesNotOverrideRemoveAction(@TempDir Path tempDir) {
        WorktreeRails.DiffSummaryRail rail = new WorktreeRails.DiffSummaryRail();
        WorktreeSession session = new WorktreeSession("/repo", tempDir.toString(), "teammate-abcd");
        session.setOriginalHeadCommit("abc123");

        assertNull(rail.beforeWorktreeExit(null, session, "remove"));
    }
}
