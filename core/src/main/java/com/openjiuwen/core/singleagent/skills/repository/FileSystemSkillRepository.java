/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Application-level filesystem Skill repository with recursive discovery.
 *
 * @since 0.1.17
 */
public final class FileSystemSkillRepository implements SkillRepository {
    private static final String[] SKILL_FILE_NAMES = {"SKILL.md", "Skill.md", "skill.md"};

    private final Path root;

    public FileSystemSkillRepository(Path root) {
        this.root = root == null ? null : root.toAbsolutePath().normalize();
    }

    @Override
    public SkillSource source() {
        return SkillSource.FILESYSTEM;
    }

    @Override
    public List<Skill> loadAll() {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        List<Path> skillFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> isSkillFileName(path.getFileName().toString()))
                    .filter(path -> isWithinRoot(path, root))
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .forEach(skillFiles::add);
        } catch (IOException ignored) {
            return List.of();
        }
        List<Skill> skills = new ArrayList<>();
        for (Path skillFile : skillFiles) {
            loadSkill(skillFile).ifPresent(skills::add);
        }
        return List.copyOf(skills);
    }

    private static boolean isSkillFileName(String fileName) {
        for (String skillFileName : SKILL_FILE_NAMES) {
            if (skillFileName.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Skill> loadSkill(Path skillFile) {
        if (!isWithinRoot(skillFile, root)) {
            Loggers.AGENT.warning(
                    "SKILL_REJECTED: action=load source=filesystem path={}"
                            + " reason=path_escape_or_symlink",
                    skillFile);
            return Optional.empty();
        }
        try {
            String text = Files.readString(skillFile, StandardCharsets.UTF_8);
            Map<String, String> frontMatter = SkillFrontMatterParser.parse(text);
            String name =
                    frontMatter.getOrDefault(
                            "name",
                            skillFile.getParent() == null
                                    ? ""
                                    : skillFile.getParent().getFileName().toString());
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }
            Skill skill = new Skill(name, frontMatter.get("description"), skillFile.getParent());
            skill.setSource(SkillSource.FILESYSTEM);
            skill.setId(frontMatter.getOrDefault("id", defaultId(root, skillFile, name)));
            skill.setProtectedSkill(
                    Boolean.parseBoolean(frontMatter.getOrDefault("protected", "false")));
            return Optional.of(skill);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isWithinRoot(Path skillFile, Path root) {
        try {
            return skillFile.toRealPath().startsWith(root.toRealPath());
        } catch (IOException exception) {
            return false;
        }
    }

    private static String defaultId(Path root, Path skillFile, String name) {
        Path relativeDirectory = skillFile.getParent();
        if (relativeDirectory == null || root == null) {
            return SkillSource.FILESYSTEM.value();
        }
        String relativePath = root.relativize(relativeDirectory).toString().replace('\\', '/');
        if (relativePath.isBlank()) {
            return SkillSource.FILESYSTEM.value() + "/" + name;
        }
        return SkillSource.FILESYSTEM.value() + "/" + relativePath;
    }
}
