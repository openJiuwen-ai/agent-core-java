package com.openjiuwen.harness.tools;

import com.openjiuwen.core.singleagent.skills.Skill;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Mirrors Python's {@code ListSkillTool} in {@code openjiuwen.harness.tools.skills.list_skill}.
 */
public class ListSkillTool extends AbstractHarnessTool {

    private final Supplier<List<Skill>> getSkills;

    public ListSkillTool(Supplier<List<Skill>> getSkills) {
        super(toolCard("list_skill", "list_skill", "List enabled skills or return a fallback selection set."), null);
        this.getSkills = getSkills;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String query = inputs.get("query") == null ? "" : String.valueOf(inputs.get("query")).trim();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skills", dumpAllSkills());
        if (query.isBlank()) {
            data.put("mode", "all");
        } else {
            data.put("mode", "all");
            data.put("message", "list_skill_model is not configured, fallback to all skills.");
        }
        return new ToolOutput(true, data, null);
    }

    private List<Map<String, Object>> dumpAllSkills() {
        return getSkills.get().stream().map(skill -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", skill.getName());
            item.put("description", skill.getDescription());
            item.put("directory", skill.getDirectory());
            item.put("skill_md_path", normalizePath(Path.of(skill.getDirectory()).resolve("SKILL.md").toString()));
            return item;
        }).toList();
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }
}
