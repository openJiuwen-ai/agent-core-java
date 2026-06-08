/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
    void branchNameFlattensPathSeparators() {
        assertEquals("worktree-feature-auth", SlugUtils.worktreeBranchName("feature-auth"));
        assertEquals("worktree-user+feature-login", SlugUtils.worktreeBranchName("user/feature-login"));
        assertEquals("worktree-a+b+c", SlugUtils.worktreeBranchName("a/b/c"));
    }

    @Test
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
