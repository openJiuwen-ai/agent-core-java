/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.core.sys_operation.Cwd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's edit-scope path checks in
 * {@code openjiuwen/auto_harness/infra/edit_scope.py}.
 */
class EditScopeTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Cwd.clear();
    }

    @Test
    void normalizeRepoPathReturnsProjectRelativePosixPath() {
        Path repoRoot = tempDir.resolve("repo");
        Path cwd = repoRoot.resolve("workspace");
        Cwd.initCwd(cwd.toString(), repoRoot.toString(), null, null);

        String normalized = EditScope.normalizeRepoPath("../openjiuwen/core/demo.py");

        assertEquals("openjiuwen/core/demo.py", normalized);
    }

    @Test
    void normalizeRepoPathKeepsAbsoluteExternalPath() {
        Path repoRoot = tempDir.resolve("repo");
        Path cwd = repoRoot.resolve("workspace");
        Path external = tempDir.resolve("outside").resolve("notes.md");
        Cwd.initCwd(cwd.toString(), repoRoot.toString(), null, null);

        String normalized = EditScope.normalizeRepoPath(external.toString());

        assertEquals(external.toAbsolutePath().normalize().toString().replace('\\', '/'), normalized);
    }

    @Test
    void allowedEditPrefixesMirrorPythonRules() {
        Path repoRoot = tempDir.resolve("repo");
        Cwd.initCwd(repoRoot.toString(), repoRoot.toString(), null, null);

        assertTrue(EditScope.isAllowedRepoEditPath("openjiuwen/core/agent.py"));
        assertTrue(EditScope.isAllowedRepoEditPath("tests/unit/test_demo.py"));
        assertTrue(EditScope.isAllowedRepoEditPath("docs/zh/guide.md"));
        assertFalse(EditScope.isAllowedRepoEditPath("openjiuwen/auto_harness/internal.py"));
    }

    @Test
    void renderEditScopeIncludesHeaderAndBoundaryRules() {
        String rendered = EditScope.renderEditScope("允许范围");

        assertTrue(rendered.contains("允许范围:"));
        assertTrue(rendered.contains("openjiuwen/harness/**"));
        assertTrue(rendered.contains("docs/en/"));
        assertTrue(rendered.contains("openjiuwen/auto_harness/**"));
    }
}
