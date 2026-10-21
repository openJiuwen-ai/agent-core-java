/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Workspace-level Skill repository. Root is normalized; only paths inside the root are accepted.
 * Each load produces an independent snapshot.
 *
 * @since 0.1.17
 */
public final class WorkspaceSkillRepository implements SkillRepository {
    private static final String[] SKILL_FILE_NAMES = {"SKILL.md", "Skill.md", "skill.md"};

    private final Path root;
    private final String namespace;
    private SkillContentValidator validator;

    public WorkspaceSkillRepository(Path root, String namespace) {
        this.root = root == null ? null : root.toAbsolutePath().normalize();
        this.namespace = namespace == null || namespace.isBlank() ? "default" : namespace;
        this.validator = new SkillContentValidator();
    }

    public void setMaxBytes(int maxBytes) {
        this.validator = new SkillContentValidator(maxBytes);
    }

    @Override
    public SkillSource source() {
        return SkillSource.WORKSPACE;
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
                    .filter(this::isWithinRoot)
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

    private boolean isWithinRoot(Path path) {
        try {
            Path realPath = path.toRealPath();
            Path realRoot = root.toRealPath();
            return realPath.startsWith(realRoot);
        } catch (IOException exception) {
            return false;
        }
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
        SkillContentValidator.ValidationResult validation = validator.validate(skillFile, root);
        if (!validation.allowed()) {
            com.openjiuwen.core.common.logging.Loggers.AGENT.warning(
                    "SKILL_REJECTED: source=workspace path={} reason={}",
                    skillFile,
                    validation.reason());
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
            String id = frontMatter.getOrDefault("id", namespace + "/" + name);
            Skill skill = new Skill(name, frontMatter.get("description"), skillFile.getParent());
            skill.setSource(SkillSource.WORKSPACE);
            skill.setId(id);
            skill.setProtectedSkill(
                    Boolean.parseBoolean(frontMatter.getOrDefault("protected", "false")));
            return Optional.of(skill);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WorkspaceSkillRepository other)) {
            return false;
        }
        return Objects.equals(root, other.root) && Objects.equals(namespace, other.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, namespace);
    }
}
