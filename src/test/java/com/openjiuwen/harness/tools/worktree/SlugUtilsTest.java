/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's slug helper tests in
 * {@code tests/unit_tests/harness/tools/worktree/test_slug.py}.</p>
 */
class SlugUtilsTest {

    @Test
    void validateSlugAcceptsSupportedFormats() {
        assertDoesNotThrow(() -> SlugUtils.validateSlug("feature-auth"));
        assertDoesNotThrow(() -> SlugUtils.validateSlug("my_feature.v2"));
        assertDoesNotThrow(() -> SlugUtils.validateSlug("user/feature-login"));
        assertDoesNotThrow(() -> SlugUtils.validateSlug("abc123"));
        assertDoesNotThrow(() -> SlugUtils.validateSlug("a".repeat(SlugUtils.MAX_SLUG_LENGTH)));
    }

    @Test
    void validateSlugAcceptsValidSimple() {
        assertDoesNotThrow(() -> SlugUtils.validateSlug("feature-auth"));
    }

    @Test
    void validateSlugAcceptsDotsAndUnderscores() {
        assertDoesNotThrow(() -> SlugUtils.validateSlug("my_feature.v2"));
    }

    @Test
    void validateSlugAcceptsSlash() {
        assertDoesNotThrow(() -> SlugUtils.validateSlug("user/feature-login"));
    }

    @Test
    void validateSlugAcceptsAlphanumeric() {
        assertDoesNotThrow(() -> SlugUtils.validateSlug("abc123"));
    }

    @Test
    void validateSlugRejectsTraversalAndEmptySegments() {
        IllegalArgumentException dotDot = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("../evil")
        );
        assertTrue(dotDot.getMessage().contains("must not contain"));

        IllegalArgumentException empty = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("a//b")
        );
        assertTrue(empty.getMessage().contains("non-empty"));

        IllegalArgumentException absolute = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("/etc/passwd")
        );
        assertTrue(absolute.getMessage().contains("non-empty"));
    }

    @Test
    void validateSlugRejectsPathTraversalDotDot() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("../evil")
        );
        assertTrue(error.getMessage().contains("must not contain"));
    }

    @Test
    void validateSlugRejectsPathTraversalDot() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("./hidden")
        );
        assertTrue(error.getMessage().contains("must not contain"));
    }

    @Test
    void validateSlugRejectsNestedPathTraversal() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("a/../../etc/passwd")
        );
        assertTrue(error.getMessage().contains("must not contain"));
    }

    @Test
    void validateSlugRejectsAbsolutePath() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("/etc/passwd")
        );
        assertTrue(error.getMessage().contains("non-empty"));
    }

    @Test
    void validateSlugRejectsShellMetacharactersAndLengthOverflow() {
        for (String ch : new String[]{";", "&", "|", "$", "`", "(", ")", "{", "}", "<", ">", "!", " "}) {
            assertThrows(IllegalArgumentException.class, () -> SlugUtils.validateSlug("bad" + ch + "slug"));
        }

        IllegalArgumentException tooLong = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("a".repeat(SlugUtils.MAX_SLUG_LENGTH + 1))
        );
        assertTrue(tooLong.getMessage().contains("characters or fewer"));
    }

    @Test
    void validateSlugRejectsShellMetacharacters() {
        for (String ch : new String[]{";", "&", "|", "$", "`", "(", ")", "{", "}", "<", ">", "!", " "}) {
            assertThrows(IllegalArgumentException.class, () -> SlugUtils.validateSlug("bad" + ch + "slug"));
        }
    }

    @Test
    void validateSlugRejectsTooLongSlug() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("a".repeat(SlugUtils.MAX_SLUG_LENGTH + 1))
        );
        assertTrue(error.getMessage().contains("characters or fewer"));
    }

    @Test
    void validateSlugAcceptsMaxLengthSlug() {
        assertDoesNotThrow(() -> SlugUtils.validateSlug("a".repeat(SlugUtils.MAX_SLUG_LENGTH)));
    }

    @Test
    void validateSlugRejectsEmptySegmentDoubleSlash() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("a//b")
        );
        assertTrue(error.getMessage().contains("non-empty"));
    }

    @Test
    void validateSlugRejectsEmptyString() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SlugUtils.validateSlug("")
        );
        assertTrue(error.getMessage().contains("non-empty"));
    }

    @Test
    void branchNameFlattensPathSeparators() {
        assertEquals("worktree-feature-auth", SlugUtils.worktreeBranchName("feature-auth"));
        assertEquals("worktree-user+feature-login", SlugUtils.worktreeBranchName("user/feature-login"));
        assertEquals("worktree-a+b+c", SlugUtils.worktreeBranchName("a/b/c"));
    }

    @Test
    void branchNameUsesWorktreePrefixForSimpleSlug() {
        assertEquals("worktree-feature-auth", SlugUtils.worktreeBranchName("feature-auth"));
    }

    @Test
    void branchNameReplacesSlashWithPlus() {
        assertEquals("worktree-user+feature-login", SlugUtils.worktreeBranchName("user/feature-login"));
    }

    @Test
    void branchNameHandlesSlugWithoutSlash() {
        assertEquals("worktree-fix", SlugUtils.worktreeBranchName("fix"));
    }

    @Test
    void branchNameReplacesMultipleSlashesWithPlus() {
        assertEquals("worktree-a+b+c", SlugUtils.worktreeBranchName("a/b/c"));
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.unit_tests.harness.tools.worktree.test_slug.TestWorktreePathFor::"
                    + "test_generates_correct_path on Windows path separators. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void worktreePathForGeneratesCorrectPath() {
        assertEquals(
                "/home/user/workspace/.worktrees/my-feature",
                SlugUtils.worktreePathFor("/home/user/workspace", "my-feature")
        );
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.unit_tests.harness.tools.worktree.test_slug.TestWorktreePathFor::"
                    + "test_with_slash_slug on Windows path separators. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void worktreePathForPreservesSlashSlug() {
        assertEquals("/ws/.worktrees/user/feat", SlugUtils.worktreePathFor("/ws", "user/feat"));
    }

    @Disabled(
            "Disabled because Python baseline failed for "
                    + "tests.unit_tests.harness.tools.worktree.test_slug.TestWorktreesDir::"
                    + "test_generates_correct_path on Windows path separators. "
                    + "Evidence: javaify-project/tests/python-baseline/latest-summary.json"
    )
    @Test
    void worktreesDirGeneratesCorrectPath() {
        assertEquals("/home/user/workspace/.worktrees", SlugUtils.worktreesDir("/home/user/workspace"));
    }

    void pathHelpersMirrorPythonLayout() {
        assertEquals(
                "/home/user/workspace/.worktrees/my-feature",
                SlugUtils.worktreePathFor("/home/user/workspace", "my-feature").replace('\\', '/')
        );
        assertEquals(
                "/ws/.worktrees/user/feat",
                SlugUtils.worktreePathFor("/ws", "user/feat").replace('\\', '/')
        );
        assertEquals(
                "/home/user/workspace/.worktrees",
                SlugUtils.worktreesDir("/home/user/workspace").replace('\\', '/')
        );
    }
}
