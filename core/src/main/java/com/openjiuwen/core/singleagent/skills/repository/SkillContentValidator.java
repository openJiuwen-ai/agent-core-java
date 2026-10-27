/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Static safety checks for workspace-level Skill files.
 *
 * <p>Validates file size, path containment, and readability. Does not execute content or attempt
 * prompt-injection detection.
 *
 * @since 0.1.17
 */
public final class SkillContentValidator {
    /**
     * Default maximum size of a workspace-level SKILL.md file.
     *
     */
    public static final int DEFAULT_WORKSPACE_MAX_BYTES = 262144;

    private final int workspaceMaxBytes;

    public SkillContentValidator() {
        this(DEFAULT_WORKSPACE_MAX_BYTES);
    }

    public SkillContentValidator(int workspaceMaxBytes) {
        this.workspaceMaxBytes = workspaceMaxBytes;
    }

    public int getWorkspaceMaxBytes() {
        return workspaceMaxBytes;
    }

    public record ValidationResult(
            boolean isAllowed, String reason, long size, String hash) {}

    /**
     * Validates that a Skill file exists, is readable, and is within the size limit.
     *
     * @param skillFile Skill file to validate
     * @return validation result with the rejection reason when disallowed
     */
    public ValidationResult validateSize(Path skillFile) {
        if (skillFile == null) {
            return rejected("null path");
        }
        if (!Files.isRegularFile(skillFile)) {
            return rejected("not a regular file");
        }
        try {
            SkillContentValidator.FileMetadata metadata = fileMetadata(skillFile);
            long size = metadata.size();
            if (size > workspaceMaxBytes) {
                return new ValidationResult(
                        false,
                        "Skill document size "
                                + size
                                + " bytes exceeds limit "
                                + workspaceMaxBytes,
                        size,
                        metadata.hash());
            }
            return new ValidationResult(true, null, size, metadata.hash());
        } catch (IOException exception) {
            return rejected("unreadable: " + exception.getMessage());
        }
    }

    /**
     * Validates containment, readability, encoding, and size for a workspace Skill file.
     *
     * @param skillFile Skill file to validate
     * @param workspaceRoot trusted workspace root
     * @return validation result with the rejection reason when disallowed
     */
    public ValidationResult validate(Path skillFile, Path workspaceRoot) {
        if (skillFile == null || workspaceRoot == null) {
            return rejected("null path");
        }
        Path normalizedFile = skillFile.toAbsolutePath().normalize();
        Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            return rejected("path outside workspace skill root");
        }
        Path realFile;
        Path realRoot;
        try {
            realFile = normalizedFile.toRealPath();
            realRoot = normalizedRoot.toRealPath();
        } catch (IOException exception) {
            if (Files.isSymbolicLink(normalizedFile)) {
                return rejected("path escape or symlink");
            }
            return rejected("unreadable: " + exception.getMessage());
        }
        if (!realFile.startsWith(realRoot)) {
            return rejected("path escape or symlink");
        }
        if (!Files.isRegularFile(realFile)) {
            return rejected("not a regular file");
        }
        FileMetadata metadata;
        try {
            metadata = fileMetadata(realFile);
        } catch (IOException exception) {
            return rejected("unreadable: " + exception.getMessage());
        }
        try {
            long size = metadata.size();
            if (size > workspaceMaxBytes) {
                return new ValidationResult(
                        false,
                        "SKILL.md size " + size + " bytes exceeds limit " + workspaceMaxBytes,
                        size,
                        metadata.hash());
            }
            Files.readString(realFile, StandardCharsets.UTF_8);
            return new ValidationResult(true, null, size, metadata.hash());
        } catch (MalformedInputException exception) {
            return new ValidationResult(
                    false,
                    "invalid UTF-8 encoding: " + exception.getMessage(),
                    metadata.size(),
                    metadata.hash());
        } catch (IOException exception) {
            return new ValidationResult(
                    false,
                    "unreadable or invalid encoding: " + exception.getMessage(),
                    -1,
                    "-");
        }
    }

    private static ValidationResult rejected(String reason) {
        return new ValidationResult(false, reason, -1, "-");
    }

    private static FileMetadata fileMetadata(Path skillFile) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream input = Files.newInputStream(skillFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                    size += read;
                }
            }
            return new FileMetadata(size, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private record FileMetadata(long size, String hash) {}
}
