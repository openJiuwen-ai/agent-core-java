/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Mirrors Python's slug helpers in
 * {@code openjiuwen/harness/tools/worktree/slug.py}.
 */
public final class SlugUtils {

    public static final int MAX_SLUG_LENGTH = 64;

    private static final Pattern VALID_SLUG_SEGMENT = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final String WORKTREES_DIR_NAME = ".worktrees";

    private SlugUtils() {
    }

    public static void validateSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid worktree name: each segment must be non-empty and contain "
                            + "only letters, digits, dots, underscores, and dashes"
            );
        }

        if (slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid worktree name: must be " + MAX_SLUG_LENGTH
                            + " characters or fewer (got " + slug.length() + ")"
            );
        }

        for (String segment : slug.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        "Invalid worktree name \"" + slug + "\": must not contain \".\" or \"..\" path segments"
                );
            }
            if (!VALID_SLUG_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException(
                        "Invalid worktree name \"" + slug + "\": each segment must be non-empty and contain "
                                + "only letters, digits, dots, underscores, and dashes"
                );
            }
        }
    }

    public static String worktreeBranchName(String slug) {
        return "worktree-" + slug.replace('/', '+');
    }

    public static String worktreePathFor(String baseDir, String slug) {
        return Path.of(baseDir, WORKTREES_DIR_NAME, slug).toString();
    }

    public static String worktreesDir(String baseDir) {
        return Path.of(baseDir, WORKTREES_DIR_NAME).toString();
    }
}
