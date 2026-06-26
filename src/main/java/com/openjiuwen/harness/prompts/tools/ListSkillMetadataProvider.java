/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ListSkillMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/list_skill.py}.
 */
public final class ListSkillMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "列出可用技能或为当前任务选择相关技能。");
        DESCRIPTIONS.put("en", "List available skills or select relevant skills for the current task.");

        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        cnSchema.put("properties", Map.of(
                "query", Map.of(
                        "type", "string",
                        "description", "可选。当前用户任务。为空时返回所有可用技能。"
                )
        ));
        cnSchema.put("required", java.util.List.of());
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        enSchema.put("properties", Map.of(
                "query", Map.of(
                        "type", "string",
                        "description", "Optional. Current user task. If empty, return all available skills."
                )
        ));
        enSchema.put("required", java.util.List.of());
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "list_skill";
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
