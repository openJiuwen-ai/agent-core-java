/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code SkillToolMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/skill_tool.py}.
 */
public final class SkillToolMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "使用此工具查看特定技能的内容");
        DESCRIPTIONS.put("en", "Use this tool to view the skill contents of a certain skill");

        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProperties = new LinkedHashMap<>();
        cnProperties.put("skill_name", Map.of(
                "type", "string",
                "description", "技能的名称"
        ));
        cnProperties.put("relative_file_path", Map.of(
                "type", "string",
                "description", "可选。查看技能目录中指定路径（relative_file_path）下的特定文件。留空则查看主 SKILL.md 文件。"
        ));
        cnSchema.put("properties", cnProperties);
        cnSchema.put("required", List.of("skill_name"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProperties = new LinkedHashMap<>();
        enProperties.put("skill_name", Map.of(
                "type", "string",
                "description", "Name of the skill"
        ));
        enProperties.put("relative_file_path", Map.of(
                "type", "string",
                "description", "Optional. Views a specific file within the skill directory at the relative_file_path. "
                        + "Leave blank to view the main SKILL.md file."
        ));
        enSchema.put("properties", enProperties);
        enSchema.put("required", List.of("skill_name"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "skill_tool";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTIONS.getOrDefault(language, DESCRIPTIONS.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return INPUT_PARAMS.getOrDefault(language, INPUT_PARAMS.get("cn"));
    }
}
