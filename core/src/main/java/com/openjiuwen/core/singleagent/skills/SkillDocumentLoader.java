/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.skills.repository.SkillContentValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads Skill main document or relative-path documents within a Skill directory.
 *
 * <p>Core implementation of the {@code skill_tool} contract. For classpath Skills, reads from
 * classpath resources. For filesystem/workspace Skills, reads from disk with path safety enforced
 * by {@link SkillPathValidator}.
 *
 * @since 0.1.17
 */
public final class SkillDocumentLoader {
    private static final String[] SKILL_FILE_NAMES = {"SKILL.md", "Skill.md", "skill.md"};

    private SkillDocumentLoader() {}

    /**
     * Reads the main document for a resolved Skill using the default configuration.
     *
     * @param skill Skill whose main document should be read
     * @return document content, or an empty value when it cannot be read
     */
    public static Optional<String> readMainDocument(Skill skill) {
        return readMainDocument(skill, SkillConfig.fromEnvironment());
    }

    /**
     * Reads the main document with the configured workspace size limit.
     *
     * @param skill Skill whose main document should be read
     * @param config Skill configuration supplying the workspace size limit
     * @return document content, or an empty value when it cannot be read
     */
    public static Optional<String> readMainDocument(Skill skill, SkillConfig config) {
        if (skill == null) {
            return Optional.empty();
        }
        for (String fileName : SKILL_FILE_NAMES) {
            Optional<String> result;
            if (skill.getSource() == SkillSource.CLASSPATH) {
                result = readFromClasspath(skill, fileName);
            } else if (skill.getSource() == SkillSource.WORKSPACE) {
                Path skillFile =
                        skill.getDirectory() == null
                                ? null
                                : skill.getDirectory().resolve(fileName);
                if (exceedsWorkspaceLimit(skill, skillFile, config)) {
                    return Optional.empty();
                }
                result = readFromFilesystem(skill, fileName, config);
            } else {
                result = readFromFilesystem(skill, fileName, config);
            }
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /**
     * Reads a controlled relative document inside the same Skill directory.
     *
     * @param skill Skill containing the relative document
     * @param relativePath document path relative to the Skill directory
     * @return document content, or an empty value when it cannot be read safely
     */
    public static Optional<String> readRelativeDocument(Skill skill, String relativePath) {
        return readRelativeDocument(skill, relativePath, SkillConfig.fromEnvironment());
    }

    /**
     * Reads a controlled relative document with the configured workspace size limit.
     *
     * @param skill Skill containing the relative document
     * @param relativePath document path relative to the Skill directory
     * @param config Skill configuration supplying the workspace size limit
     * @return document content, or an empty value when it cannot be read safely
     */
    public static Optional<String> readRelativeDocument(
            Skill skill, String relativePath, SkillConfig config) {
        if (skill == null || relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }
        if (!SkillPathValidator.isSafeRelativePath(relativePath)) {
            logRejected(skill, relativePath, "path_escape");
            return Optional.empty();
        }
        if (skill.getSource() == SkillSource.CLASSPATH) {
            return readFromClasspath(skill, relativePath);
        }
        return readFromFilesystem(skill, relativePath, config);
    }

    private static Optional<String> readFromClasspath(Skill skill, String relativePath) {
        if (skill.getDirectory() == null) {
            return Optional.empty();
        }
        String resourcePath =
                skill.getDirectory().toString().replace('\\', '/')
                        + "/"
                        + relativePath.replace('\\', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = SkillDocumentLoader.class.getClassLoader();
        }
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> readFromFilesystem(
            Skill skill, String relativePath, SkillConfig config) {
        Optional<Path> resolved = SkillPathValidator.resolveSafe(skill, relativePath);
        if (resolved.isEmpty()) {
            logRejected(skill, relativePath, "path_escape_or_symlink");
            return Optional.empty();
        }
        if (!Files.isRegularFile(resolved.get())) {
            return Optional.empty();
        }
        if (exceedsWorkspaceLimit(skill, resolved.get(), config)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(resolved.get(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static boolean exceedsWorkspaceLimit(Skill skill, Path resolved, SkillConfig config) {
        if (skill.getSource() != SkillSource.WORKSPACE
                || resolved == null
                || !Files.isRegularFile(resolved)) {
            return false;
        }
        SkillContentValidator.ValidationResult result =
                new SkillContentValidator(
                                config == null
                                        ? SkillConfig.DEFAULT_WORKSPACE_MAX_BYTES
                                        : config.getWorkspaceMaxBytes())
                        .validateSize(resolved);
        if (result.isAllowed()) {
            return false;
        }
        logRejected(skill, resolved.toString(), result.reason());
        return true;
    }

    private static void logRejected(Skill skill, String relativePath, String reason) {
        Loggers.AGENT.warning(
                "SKILL_REJECTED: action=document_read skill_id={} source={} path={} reason={}",
                skill.getId() == null || skill.getId().isBlank() ? skill.getName() : skill.getId(),
                skill.getSource() == null
                        ? SkillSource.FILESYSTEM.value()
                        : skill.getSource().value(),
                relativePath,
                reason);
    }
}
