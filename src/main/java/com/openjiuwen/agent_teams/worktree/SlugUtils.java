/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Slug validation and branch naming for worktrees.
 *
 * <p>Provides safe slug validation (path traversal prevention, length limits)
 * and deterministic branch/path derivation from slugs.</p>
 *
 * <p>Mirrors Python's {@code slug} in {@code openjiuwen.agent_teams.worktree.slug}.</p>
 */
public final class SlugUtils {

    private static final Pattern VALID_SLUG_SEGMENT = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int MAX_SLUG_LENGTH = 64;
    private static final String WORKTREES_DIR_NAME = ".worktrees";

    private SlugUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Validate worktree slug for safety.
     *
     * <p>Rejects path traversal, absolute paths, shell metacharacters,
     * and overly long names.</p>
     *
     * @param slug The worktree name to validate.
     * @throws IllegalArgumentException If slug is invalid, with specific reason.
     */
    public static void validateSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("Invalid worktree name: slug must not be null or empty");
        }

        if (slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Invalid worktree name: must be %d characters or fewer (got %d)",
                    MAX_SLUG_LENGTH, slug.length()));
        }

        for (String segment : slug.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                    String.format("Invalid worktree name \"%s\": must not contain \".\" or \"..\" path segments", slug));
            }
            if (!VALID_SLUG_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException(
                    String.format("Invalid worktree name \"%s\": each segment must be non-empty and contain "
                        + "only letters, digits, dots, underscores, and dashes", slug));
            }
        }
    }

    /**
     * Convert slug to git branch name.
     *
     * <p>Flattens "/" to "+" to avoid directory/file conflicts
     * in git refs namespace.</p>
     *
     * @param slug Validated worktree slug.
     * @return Branch name in the format "worktree-<flattened-slug>".
     *
     * @example
     * "feature-auth"       -> "worktree-feature-auth"
     * "user/feature-login" -> "worktree-user+feature-login"
     */
    public static String worktreeBranchName(String slug) {
        return "worktree-" + slug.replace('/', '+');
    }

    /**
     * Compute worktree directory path under a base directory.
     *
     * <p>Worktrees live in {@code {base_dir}/.worktrees/{slug}}. {@code base_dir}
     * is normally the owning DeepAgent's workspace root, so each agent's
     * worktrees are isolated under its own workspace rather than the
     * source git repository.</p>
     *
     * @param baseDir Absolute path to the directory that owns the
     *                worktrees subtree (typically the DeepAgent workspace root).
     * @param slug    Validated worktree slug.
     * @return Absolute path to the worktree directory.
     */
    public static String worktreePathFor(String baseDir, String slug) {
        return Path.of(baseDir, WORKTREES_DIR_NAME, slug).toString();
    }

    /**
     * Return the parent directory for all worktrees under {@code baseDir}.
     *
     * @param baseDir Absolute path to the directory that owns the
     *                worktrees subtree (typically the DeepAgent workspace root).
     * @return Absolute path to the worktrees parent directory.
     */
    public static String worktreesDir(String baseDir) {
        return Path.of(baseDir, WORKTREES_DIR_NAME).toString();
    }
}