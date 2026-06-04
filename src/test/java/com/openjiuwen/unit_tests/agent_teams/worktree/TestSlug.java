/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.SlugUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_slug}.
 */
class TestSlug {

    private static final int MAX_SLUG_LENGTH = 64;

    @Nested
    class TestValidateSlug {
        @Test
        void testValidSimple() {
            assertDoesNotThrow(() -> SlugUtils.validateSlug("feature-auth"));
        }

        @Test
        void testValidWithDotsUnderscores() {
            assertDoesNotThrow(() -> SlugUtils.validateSlug("my_feature.v2"));
        }

        @Test
        void testValidWithSlash() {
            assertDoesNotThrow(() -> SlugUtils.validateSlug("user/feature-login"));
        }

        @Test
        void testValidAlphanumeric() {
            assertDoesNotThrow(() -> SlugUtils.validateSlug("abc123"));
        }

        @Test
        void testPathTraversalDotdot() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> SlugUtils.validateSlug("../evil")
            );
            assertTrue(error.getMessage().contains("must not contain"));
        }

        @Test
        void testPathTraversalDot() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> SlugUtils.validateSlug("./hidden")
            );
            assertTrue(error.getMessage().contains("must not contain"));
        }

        @Test
        void testPathTraversalNested() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> SlugUtils.validateSlug("a/../../etc/passwd")
            );
            assertTrue(error.getMessage().contains("must not contain"));
        }

        @Test
        void testAbsolutePathRejected() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> SlugUtils.validateSlug("/etc/passwd")
            );
            assertTrue(error.getMessage().contains("non-empty"));
        }

        @Test
        void testShellMetacharactersRejected() {
            for (String character : new String[] {";", "&", "|", "$", "`", "(", ")", "{", "}", "<", ">", "!", " "}) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SlugUtils.validateSlug("bad" + character + "slug")
                );
            }
        }

        @Test
        void testTooLong() {
            String slug = "a".repeat(MAX_SLUG_LENGTH + 1);

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> SlugUtils.validateSlug(slug)
            );
            assertTrue(error.getMessage().contains("characters or fewer"));
        }

        @Test
        void testMaxLengthOk() {
            String slug = "a".repeat(MAX_SLUG_LENGTH);

            assertDoesNotThrow(() -> SlugUtils.validateSlug(slug));
        }

        @Test
        void testEmptySegmentDoubleSlash() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> SlugUtils.validateSlug("a//b")
            );
            assertTrue(error.getMessage().contains("non-empty"));
        }

        @Test
        void testEmptyString() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> SlugUtils.validateSlug("")
            );
            assertTrue(error.getMessage().contains("non-empty"));
        }
    }

    @Nested
    class TestWorktreeBranchName {
        @Test
        void testSimple() {
            assertEquals("worktree-feature-auth", SlugUtils.worktreeBranchName("feature-auth"));
        }

        @Test
        void testWithSlash() {
            assertEquals("worktree-user+feature-login", SlugUtils.worktreeBranchName("user/feature-login"));
        }

        @Test
        void testNoSlash() {
            assertEquals("worktree-fix", SlugUtils.worktreeBranchName("fix"));
        }

        @Test
        void testMultipleSlashes() {
            assertEquals("worktree-a+b+c", SlugUtils.worktreeBranchName("a/b/c"));
        }
    }

    @Nested
    class TestWorktreePathFor {
        @Test
        void testGeneratesCorrectPath() {
            String result = SlugUtils.worktreePathFor("/home/user/workspace", "my-feature");

            assertEquals(Path.of("/home/user/workspace", ".worktrees", "my-feature").toString(), result);
        }

        @Test
        void testWithSlashSlug() {
            String result = SlugUtils.worktreePathFor("/ws", "user/feat");

            assertEquals(Path.of("/ws", ".worktrees", "user", "feat").toString(), result);
        }
    }

    @Nested
    class TestWorktreesDir {
        @Test
        void testGeneratesCorrectPath() {
            String result = SlugUtils.worktreesDir("/home/user/workspace");

            assertEquals(Path.of("/home/user/workspace", ".worktrees").toString(), result);
        }
    }
}
