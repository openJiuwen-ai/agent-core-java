/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validates relative paths used by {@code skill_tool} to read documents inside a Skill directory.
 * Rejects path escapes, absolute paths, drive letters, UNC, and traversal sequences.
 *
 * @since 0.1.17
 */
public final class SkillPathValidator {
    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:");
    private static final Pattern UNC_PREFIX = Pattern.compile("^\\\\\\\\");

    private SkillPathValidator() {}

    /**
     * Returns whether a Skill-relative path stays inside the Skill directory.
     *
     * @param relativePath path relative to the Skill directory
     * @return true when the path is non-empty and does not escape the directory
     */
    public static boolean isSafeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            return false;
        }
        if (WINDOWS_DRIVE.matcher(relativePath).find() || UNC_PREFIX.matcher(relativePath).find()) {
            return false;
        }
        try {
            Path candidate = Path.of(relativePath).normalize();
            if (candidate.isAbsolute()) {
                return false;
            }
            for (Path part : candidate) {
                if ("..".equals(part.toString())) {
                    return false;
                }
            }
            return !candidate.toString().isBlank();
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    /**
     * Resolves a Skill-relative path after rejecting every escape form.
     *
     * @param skill Skill whose directory is the resolution root
     * @param relativePath path relative to the Skill directory
     * @return resolved path, or an empty value when the path is unsafe
     */
    public static Optional<Path> resolveSafe(Skill skill, String relativePath) {
        if (skill == null || skill.getDirectory() == null || !isSafeRelativePath(relativePath)) {
            return Optional.empty();
        }
        try {
            Path resolved = skill.getDirectory().resolve(relativePath).normalize();
            Path realResolved = resolved.toRealPath();
            Path realRoot = skill.getDirectory().toRealPath();
            if (!realResolved.startsWith(realRoot)) {
                return Optional.empty();
            }
            return Optional.of(resolved);
        } catch (java.io.IOException exception) {
            return Optional.empty();
        }
    }
}
