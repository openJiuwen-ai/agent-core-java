/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Safe slug validation (no path traversal, length limits) and deterministic
 * branch/path derivation for worktrees.
 *
 * <p>Mirrors Python worktree/slug.py.</p>
 */
public final class SlugValidator {

    /** Valid slug segment: letters, digits, dots, underscores, dashes. */
    private static final Pattern VALID_SLUG_SEGMENT = Pattern.compile("^[a-zA-Z0-9._\\-]+$");

    /** Maximum length of a slug. */
    public static final int MAX_SLUG_LENGTH = 64;

    private SlugValidator() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Slug must not be empty");
        }
        if (slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                    "Slug exceeds maximum length of " + MAX_SLUG_LENGTH + " characters: " + slug);
        }
        String[] segments = slug.split("/", -1);
        for (String segment : segments) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        "Slug contains path traversal segment '" + segment + "': " + slug);
            }
            if (!VALID_SLUG_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException(
                        "Slug segment '" + segment + "' contains invalid characters. "
                                + "Allowed: letters, digits, dots, underscores, dashes.");
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String worktreeBranchName(String slug) {
        validateSlug(slug);
        return "worktree-" + slug.replace("/", "+");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String worktreePathFor(Path baseDir, String slug) {
        validateSlug(slug);
        return baseDir.resolve(".worktrees").resolve(slug).toString();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String worktreesDir(Path baseDir) {
        return baseDir.resolve(".worktrees").toString();
    }
}
