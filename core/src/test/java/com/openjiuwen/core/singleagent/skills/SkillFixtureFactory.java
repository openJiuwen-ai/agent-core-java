/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Creates temporary filesystem or workspace Skill fixtures for repository tests.
 *
 * @since 0.1.17
 */
public final class SkillFixtureFactory {
    private static final String SKILL_FILE_NAME = "SKILL.md";

    /**
     * Repository types supported by Skill fixtures.
     */
    public enum SkillFixtureSource {
        CLASSPATH("classpath"),
        FILESYSTEM("filesystem"),
        WORKSPACE("workspace");

        private final String directoryName;

        SkillFixtureSource(String directoryName) {
            this.directoryName = directoryName;
        }

        public String directoryName() {
            return directoryName;
        }
    }

    public record SkillFixture(SkillFixtureSource source, Path skillFile) {}

    private SkillFixtureFactory() {}

    /**
     * Writes a Skill fixture beneath the source-specific repository directory.
     *
     * @param temporaryRoot root containing all fixture repositories
     * @param source repository type for the fixture
     * @param skillName fixture skill name
     * @param body fixture Skill body
     * @param isProtected whether the fixture overrides are protected
     * @return the created fixture
     * @throws IOException if the fixture cannot be written
     */
    public static SkillFixture writeSkill(
            Path temporaryRoot,
            SkillFixtureSource source,
            String skillName,
            String body,
            boolean isProtected)
            throws IOException {
        Objects.requireNonNull(temporaryRoot, "temporaryRoot must not be null");
        Objects.requireNonNull(source, "source must not be null");
        String description = source.name().toLowerCase(Locale.ROOT) + "-" + skillName;
        Path repositoryRoot = temporaryRoot.resolve(source.directoryName());
        Path skillFile = writeSkill(repositoryRoot, skillName, description, body, isProtected);
        return new SkillFixture(source, skillFile);
    }

    /**
     * Writes a Skill fixture directly beneath the specified repository root.
     *
     * @param repositoryRoot repository directory for the fixture
     * @param skillName fixture skill name
     * @param description fixture Skill description
     * @param body fixture Skill body
     * @param isProtected whether the fixture overrides are protected
     * @return the created SKILL.md path
     * @throws IOException if the fixture cannot be written
     */
    public static Path writeSkill(
            Path repositoryRoot,
            String skillName,
            String description,
            String body,
            boolean isProtected)
            throws IOException {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot must not be null");
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Path skillDirectory = repositoryRoot.resolve(skillName);
        Files.createDirectories(skillDirectory);
        Path skillFile = skillDirectory.resolve(SKILL_FILE_NAME);
        Files.writeString(
                skillFile,
                skillMarkdown(skillName, description, body, isProtected),
                StandardCharsets.UTF_8);
        return skillFile;
    }

    private static String skillMarkdown(
            String skillName, String description, String body, boolean isProtected) {
        StringBuilder markdown =
                new StringBuilder("---\n")
                        .append("name: ")
                        .append(skillName)
                        .append('\n')
                        .append("description: ")
                        .append(description)
                        .append('\n');
        if (isProtected) {
            markdown.append("protected: true\n");
        }
        return markdown.append("---\n\n").append(body).append('\n').toString();
    }
}
