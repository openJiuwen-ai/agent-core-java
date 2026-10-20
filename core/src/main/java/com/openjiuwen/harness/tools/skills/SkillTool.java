/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.skills;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillDocumentLoader;
import com.openjiuwen.core.singleagent.skills.SkillSource;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Reads the content of a selected skill file. */
public class SkillTool extends AbstractHarnessTool {

    private final Supplier<List<SkillDescriptor>> skillsSupplier;

    public SkillTool(Supplier<List<SkillDescriptor>> skillsSupplier) {
        super(toolCard("skill_tool", "skill_tool", "View the skill contents of a certain skill."));
        this.skillsSupplier = skillsSupplier;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String skillName = stringValue(inputs == null ? null : inputs.get("skill_name")).trim();
        String skillId = stringValue(inputs == null ? null : inputs.get("skill_id")).trim();
        String skillPath = stringValue(inputs == null ? null : inputs.get("skill_path")).trim();
        Object requestedPath = inputs == null ? null : inputs.get("relative_path");
        if (requestedPath == null) {
            requestedPath = inputs == null ? null : inputs.get("relative_file_path");
        }
        String relativePath = stringValue(requestedPath).trim();
        Optional<SkillDescriptor> skill = getSkill(skillName, skillId, skillPath);
        if (skill.isEmpty()) {
            return ToolOutput.failure("Skill not found: " + skillName);
        }
        try {
            Skill skillModel = toSkillModel(skill.get());
            Optional<String> content =
                    isMainDocument(relativePath)
                            ? SkillDocumentLoader.readMainDocument(skillModel)
                            : SkillDocumentLoader.readRelativeDocument(skillModel, relativePath);
            if (content.isEmpty()) {
                return ToolOutput.failure(
                        "Document not found: "
                                + (relativePath.isEmpty() ? "SKILL.md" : relativePath));
            }
            return ToolOutput.success(
                    Map.of(
                            "skill_directory", skill.get().directory(),
                            "skill_content", content.get()));
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    private static boolean isMainDocument(String relativePath) {
        return relativePath.isEmpty()
                || "SKILL.md".equalsIgnoreCase(relativePath)
                || "Skill.md".equalsIgnoreCase(relativePath)
                || "skill.md".equalsIgnoreCase(relativePath);
    }

    static Skill toSkillModel(SkillDescriptor descriptor) {
        Skill skill =
                new Skill(
                        descriptor.name(),
                        descriptor.description(),
                        Path.of(descriptor.directory()));
        Object source = descriptor.metadata() == null ? null : descriptor.metadata().get("source");
        skill.setSource(parseSkillSource(source));
        Object id = descriptor.metadata() == null ? null : descriptor.metadata().get("id");
        if (id != null && !String.valueOf(id).isBlank()) {
            skill.setId(String.valueOf(id));
        }
        Object protectedSkill =
                descriptor.metadata() == null ? null : descriptor.metadata().get("protected");
        skill.setProtectedSkill(Boolean.parseBoolean(String.valueOf(protectedSkill)));
        return skill;
    }

    private static SkillSource parseSkillSource(Object source) {
        String value = String.valueOf(source);
        if ("classpath".equalsIgnoreCase(value)) {
            return SkillSource.CLASSPATH;
        }
        if ("workspace".equalsIgnoreCase(value)) {
            return SkillSource.WORKSPACE;
        }
        return SkillSource.FILESYSTEM;
    }

    private Optional<SkillDescriptor> getSkill(String skillName, String skillId, String skillPath) {
        List<SkillDescriptor> skills = skillsSupplier == null ? List.of() : skillsSupplier.get();
        if (skills == null) {
            return Optional.empty();
        }
        boolean hasReference = !skillName.isBlank() || !skillId.isBlank() || !skillPath.isBlank();
        if (!hasReference) {
            return Optional.empty();
        }
        for (SkillDescriptor skill : skills) {
            if (skill == null) {
                continue;
            }
            Object descriptorId = skill.metadata() == null ? null : skill.metadata().get("id");
            boolean idMatches = skillId.isBlank() || skillId.equals(String.valueOf(descriptorId));
            boolean pathMatches = skillPath.isBlank() || pathMatches(skill, skillPath);
            boolean nameMatches = skillName.isBlank() || skillName.equals(skill.name());
            if (idMatches && pathMatches && nameMatches) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    private static boolean pathMatches(SkillDescriptor skill, String requestedPath) {
        if (skill.metadata() != null
                && SkillSource.CLASSPATH
                        .value()
                        .equalsIgnoreCase(String.valueOf(skill.metadata().get("source")))) {
            return normalizeClasspath(skill.directory()).equals(normalizeClasspath(requestedPath));
        }
        try {
            Path requested = Path.of(requestedPath).toAbsolutePath().normalize().toRealPath();
            Path directory = Path.of(skill.directory()).toRealPath();
            if (requested.equals(directory)) {
                return true;
            }
            return requested.getParent() != null
                    && requested.getParent().equals(directory)
                    && ("SKILL.md".equalsIgnoreCase(requested.getFileName().toString())
                            || "Skill.md".equalsIgnoreCase(requested.getFileName().toString())
                            || "skill.md".equalsIgnoreCase(requested.getFileName().toString()));
        } catch (IOException exception) {
            return false;
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private static String normalizeClasspath(String path) {
        String normalized = path.replace('\\', '/');
        String classpathPrefix = SkillSource.CLASSPATH.value();
        if (normalized.startsWith(classpathPrefix)) {
            normalized = normalized.substring(classpathPrefix.length());
        }
        if (normalized.startsWith(String.valueOf(':'))) {
            normalized = normalized.substring(1);
        }
        while (normalized.startsWith(String.valueOf('/'))) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(String.valueOf('/'))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
