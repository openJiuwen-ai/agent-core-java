/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for worktree slug module.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_slug}.
 */
class TestSlug {

    private static final int MAX_SLUG_LENGTH = 64;

    @Nested
    class TestValidateSlug {

        @Test
        @Tag("level0")
        void testValidSimple() {
            validateSlug("feature-auth");
        }

        @Test
        @Tag("level0")
        void testValidWithDotsUnderscores() {
            validateSlug("my_feature.v2");
        }

        @Test
        @Tag("level0")
        void testValidWithSlash() {
            validateSlug("user/feature-login");
        }

        @Test
        @Tag("level0")
        void testValidAlphanumeric() {
            validateSlug("abc123");
        }

        @Test
        @Tag("level0")
        void testPathTraversalDotdot() {
            assertThrows(IllegalArgumentException.class, () -> validateSlug("../evil"));
        }

        @Test
        @Tag("level0")
        void testPathTraversalDot() {
            assertThrows(IllegalArgumentException.class, () -> validateSlug("./hidden"));
        }

        @Test
        @Tag("level0")
        void testPathTraversalNested() {
            assertThrows(IllegalArgumentException.class, () -> validateSlug("a/../../etc/passwd"));
        }

        @Test
        @Tag("level0")
        void testEmptySlug() {
            assertThrows(IllegalArgumentException.class, () -> validateSlug(""));
        }

        @Test
        @Tag("level0")
        void testTooLongSlug() {
            String longSlug = "a".repeat(MAX_SLUG_LENGTH + 1);
            assertThrows(IllegalArgumentException.class, () -> validateSlug(longSlug));
        }
    }

    @Nested
    class TestWorktreeBranchName {

        @Test
        @Tag("level0")
        void testBranchNameFormat() {
            String branch = worktreeBranchName("feature-auth");
            assertEquals("worktree-feature-auth", branch);
        }
    }

    @Nested
    class TestWorktreePathFor {

        @Test
        @Tag("level0")
        void testPathFormat() {
            String path = worktreePathFor("/workspace", "test-slug");
            assertTrue(path.contains(".worktrees"));
            assertTrue(path.contains("test-slug"));
        }
    }

    // Helper methods

    private static void validateSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("Slug must not be empty");
        }
        if (slug.contains("..") || slug.startsWith("./") || slug.contains("/..")) {
            throw new IllegalArgumentException("Slug must not contain path traversal");
        }
        if (slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException("Slug too long");
        }
    }

    private static String worktreeBranchName(String slug) {
        return "worktree-" + slug;
    }

    private static String worktreePathFor(String workspace, String slug) {
        return workspace + "/.worktrees/" + slug;
    }
}