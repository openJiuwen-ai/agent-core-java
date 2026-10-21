/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Static safety checks for workspace-level Skill files.
 *
 * <p>Validates file size, path containment, and readability. Does not execute content or attempt
 * prompt-injection detection.
 *
 * @since 0.1.17
 */
public final class SkillContentValidator {
    private final int workspaceMaxBytes;

    /** Default maximum size for a workspace SKILL.md file in bytes. */
    public static final int DEFAULT_WORKSPACE_MAX_BYTES = 262144;

    public SkillContentValidator() {
        this(DEFAULT_WORKSPACE_MAX_BYTES);
    }

    public SkillContentValidator(int workspaceMaxBytes) {
        this.workspaceMaxBytes = workspaceMaxBytes;
    }

    public int getWorkspaceMaxBytes() {
        return workspaceMaxBytes;
    }

    public record ValidationResult(boolean allowed, String reason) {}

    /**
     * Validates that a Skill file is readable and within the size limit.
     *
     * @param skillFile Skill file to validate
     * @return validation result with a reason on failure
     */
    public ValidationResult validateSize(Path skillFile) {
        if (skillFile == null) {
            return new ValidationResult(false, "null path");
        }
        if (!Files.isRegularFile(skillFile)) {
            return new ValidationResult(false, "not a regular file");
        }
        try {
            long size = Files.size(skillFile);
            if (size > workspaceMaxBytes) {
                return new ValidationResult(
                        false,
                        "Skill document size "
                                + size
                                + " bytes exceeds limit "
                                + workspaceMaxBytes);
            }
            return new ValidationResult(true, null);
        } catch (IOException exception) {
            return new ValidationResult(
                    false, "unreadable or invalid encoding: " + exception.getMessage());
        }
    }

    /**
     * Validates size, UTF-8 readability, and workspace-root containment.
     *
     * @param skillFile Skill file to validate
     * @param workspaceRoot root that must contain the Skill file
     * @return validation result with a reason on failure
     */
    public ValidationResult validate(Path skillFile, Path workspaceRoot) {
        if (skillFile == null || workspaceRoot == null) {
            return new ValidationResult(false, "null path");
        }
        Path normalizedFile = skillFile.toAbsolutePath().normalize();
        Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            return new ValidationResult(false, "path outside workspace skill root");
        }
        if (!Files.isRegularFile(normalizedFile)) {
            return new ValidationResult(false, "not a regular file");
        }
        try {
            long size = Files.size(normalizedFile);
            if (size > workspaceMaxBytes) {
                return new ValidationResult(
                        false,
                        "SKILL.md size " + size + " bytes exceeds limit " + workspaceMaxBytes);
            }
            Files.readString(normalizedFile, StandardCharsets.UTF_8);
            return new ValidationResult(true, null);
        } catch (IOException exception) {
            return new ValidationResult(
                    false, "unreadable or invalid encoding: " + exception.getMessage());
        }
    }
}
