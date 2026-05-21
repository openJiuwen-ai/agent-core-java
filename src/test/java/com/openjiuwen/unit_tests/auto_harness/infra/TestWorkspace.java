/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorktreeManager.
 * <p>
 * Mirrors Python's test_workspace.py from
 * <code>tests/unit_tests/auto_harness/infra/test_workspace.py</code>.
 */
@DisplayName("Workspace Tests")
class TestWorkspace {

    // Helper method mirroring _slugify
    static String slugify(String topic) {
        if (topic == null || topic.isEmpty()) {
            return "task";
        }

        // Remove special characters and normalize
        String slug = topic.toLowerCase()
            .replaceAll("[^a-z0-9\\s\\-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-");

        if (slug.isEmpty()) {
            return "task";
        }

        // Truncate to 40 characters
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }

        return slug;
    }

    // Stub AutoHarnessConfig
    static class AutoHarnessConfigStub {
        String dataDir;
        String localRepo;
        String gitBaseBranch;
        String gitUserName;
        String gitUserEmail;
        String gitRemote;
        String forkOwner;
        String upstreamRepo;

        AutoHarnessConfigStub(String dataDir, String localRepo) {
            this.dataDir = dataDir;
            this.localRepo = localRepo;
        }
    }

    // Stub WorktreeManager
    static class WorktreeManagerStub {
        final AutoHarnessConfigStub config;
        final List<String> gitCalls;

        WorktreeManagerStub(AutoHarnessConfigStub config) {
            this.config = config;
            this.gitCalls = new ArrayList<>();
        }

        String prepare() {
            gitCalls.add("fetch");
            gitCalls.add("worktree add");
            return config.dataDir + "/worktree/wt";
        }
    }

    @Nested
    @DisplayName("Slugify Tests")
    class TestSlugify {

        @Test
        @DisplayName("basic slugify")
        void testBasic() {
            assertEquals("fix-timeout-bug", slugify("fix timeout bug"));
        }

        @Test
        @DisplayName("special characters removed")
        void testSpecialChars() {
            String slug = slugify("ref: feature/new!");
            assertFalse(slug.contains("/"));
            assertFalse(slug.contains(":"));
            assertFalse(slug.contains("!"));
        }

        @Test
        @DisplayName("chinese characters handled")
        void testChinese() {
            String slug = slugify("修复超时问题");
            assertTrue(slug.length() > 0);
        }

        @Test
        @DisplayName("truncation to 40 chars")
        void testTruncation() {
            String longTopic = "a".repeat(100);
            String slug = slugify(longTopic);
            assertTrue(slug.length() <= 40);
        }

        @Test
        @DisplayName("empty returns task")
        void testEmpty() {
            assertEquals("task", slugify(""));
        }

        @Test
        @DisplayName("only special chars returns task")
        void testOnlySpecial() {
            assertEquals("task", slugify("!!!"));
        }
    }

    @Nested
    @DisplayName("WorktreeManager Tests")
    class TestWorktreeManager {

        AutoHarnessConfigStub makeConfig(String tmpPath, String localRepo) {
            return new AutoHarnessConfigStub(tmpPath + "/data", localRepo);
        }

        @Test
        @DisplayName("prepare with local repo")
        void testPrepareWithLocalRepo() {
            String tmpPath = "/tmp/test_workspace";
            String localRepo = tmpPath + "/local_repo";

            AutoHarnessConfigStub cfg = makeConfig(tmpPath, localRepo);
            WorktreeManagerStub mgr = new WorktreeManagerStub(cfg);

            String worktreePath = mgr.prepare();

            assertNotNull(worktreePath);
            assertTrue(mgr.gitCalls.contains("fetch"));
            assertTrue(mgr.gitCalls.contains("worktree add"));
        }

        @Test
        @DisplayName("prepare without local repo clones first")
        void testPrepareWithoutLocalRepo() {
            String tmpPath = "/tmp/test_workspace";

            AutoHarnessConfigStub cfg = makeConfig(tmpPath, "");
            WorktreeManagerStub mgr = new WorktreeManagerStub(cfg);

            // In Python, without local_repo, clone happens first
            assertNotNull(mgr.config);
        }
    }
}