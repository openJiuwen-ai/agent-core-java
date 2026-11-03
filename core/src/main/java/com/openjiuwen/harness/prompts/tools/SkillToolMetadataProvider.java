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
        DESCRIPTIONS.put(
                "en",
                "Read skill documents. Select a skill by skill_name, skill_id, or skill_path;"
                        + " use relative_path='SKILL.md' for the main document or a relative path"
                        + " for extra files.");

        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProperties = new LinkedHashMap<>();
        cnProperties.put("skill_name", Map.of(
                "type", "string",
                "description", "技能的名称"
        ));
        cnProperties.put("skill_id", Map.of(
                "type", "string",
                "description", "可选。显式指定稳定 Skill id，用于选择非默认层。"
        ));
        cnProperties.put("skill_path", Map.of(
                "type", "string",
                "description", "可选。显式指定受信任仓库内的 Skill 目录或 SKILL.md 路径。"
        ));
        cnProperties.put("relative_path", Map.of(
                "type", "string",
                "description", "可选。查看技能目录中指定路径（relative_path）下的特定文件。留空则查看主 SKILL.md 文件。"
        ));
        cnSchema.put("properties", cnProperties);
        cnSchema.put("required", List.of());
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProperties = new LinkedHashMap<>();
        enProperties.put("skill_name", Map.of(
                "type", "string",
                "description", "Name of the skill to read"
        ));
        enProperties.put("skill_id", Map.of(
                "type", "string",
                "description", "Optional stable Skill id. Prefer this to select a non-default Skill layer."
        ));
        enProperties.put("skill_path", Map.of(
                "type", "string",
                "description", "Optional trusted Skill directory or SKILL.md path. Must resolve "
                        + "to a registered repository."
        ));
        enProperties.put("relative_path", Map.of(
                "type", "string",
                "description", "Relative path within skill directory (default: SKILL.md)"
        ));
        enSchema.put("properties", enProperties);
        enSchema.put("required", List.of());
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
