/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.skills;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Reads the content of a selected skill file.
 *
 * <p>Mirrors Python's {@code SkillTool} in
 * {@code openjiuwen/harness/tools/skills/skill_tool.py}.</p>
 */
public class SkillTool extends AbstractHarnessTool {

    private final Supplier<List<SkillDescriptor>> skillsSupplier;

    public SkillTool(Supplier<List<SkillDescriptor>> skillsSupplier) {
        super(toolCard("skill_tool", "SkillTool", "View the skill contents of a certain skill."));
        this.skillsSupplier = skillsSupplier;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String skillName = stringValue(inputs == null ? null : inputs.get("skill_name")).trim();
        String relativePath = stringValue(inputs == null ? "SKILL.md" : inputs.getOrDefault("relative_file_path", "SKILL.md"))
                .trim();
        relativePath = relativePath.isEmpty() ? "SKILL.md" : relativePath;
        SkillDescriptor skill = getSkillByName(skillName);
        if (skill == null) {
            return ToolOutput.failure("Skill not found: " + skillName);
        }
        try {
            Path filePath = Path.of(skill.directory()).resolve(relativePath).normalize();
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return ToolOutput.success(Map.of(
                    "skill_directory", skill.directory(),
                    "skill_content", content
            ));
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    private SkillDescriptor getSkillByName(String skillName) {
        List<SkillDescriptor> skills = skillsSupplier == null ? List.of() : skillsSupplier.get();
        if (skills == null) {
            return null;
        }
        for (SkillDescriptor skill : skills) {
            if (skill != null && skillName.equals(skill.name())) {
                return skill;
            }
        }
        return null;
    }
}
