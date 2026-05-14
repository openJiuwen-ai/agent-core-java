package com.openjiuwen.harness.tools;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.sysop.SysOperation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Mirrors Python's {@code SkillTool} in {@code openjiuwen.harness.tools.skills.skill_tool}.
 */
public class SkillTool extends AbstractHarnessTool {

    private final Supplier<List<Skill>> getSkills;

    public SkillTool(SysOperation sysOperation, Supplier<List<Skill>> getSkills) {
        super(toolCard("skill_tool", "skill_tool", "Read the content of an enabled skill file."), sysOperation);
        this.getSkills = getSkills;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String skillName = stringValue(inputs.get("skill_name"));
        String relativeFilePath = stringValue(inputs.get("relative_file_path"));
        if (relativeFilePath.isBlank()) {
            relativeFilePath = "SKILL.md";
        }
        Skill skill = findSkill(skillName);
        if (skill == null) {
            return new ToolOutput(false, null, "Skill not found: " + skillName);
        }
        try {
            Path filePath = Path.of(skill.getDirectory()).resolve(relativeFilePath);
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("skill_directory", skill.getDirectory());
            data.put("skill_content", content);
            return new ToolOutput(true, data, null);
        } catch (Exception e) {
            return new ToolOutput(false, null, e.getMessage());
        }
    }

    private Skill findSkill(String name) {
        return getSkills.get().stream().filter(skill -> skill.getName().equals(name)).findFirst().orElse(null);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
