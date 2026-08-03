/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code LoadToolsMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/load_tools.py}.
 */
public final class LoadToolsMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "将选定的真实工具加载到当前 session 可见工具集合中。");
        DESCRIPTIONS.put("en", "Load selected real tools into the current session-visible tool set.");

        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnToolNames = new LinkedHashMap<>();
        cnToolNames.put("type", "array");
        cnToolNames.put("items", Map.of("type", "string"));
        cnToolNames.put("description", "要在当前 session 中可见的工具名称列表");
        cnSchema.put("properties", Map.of(
                "tool_names", cnToolNames,
                "replace", Map.of(
                        "type", "boolean",
                        "description", "如果为 true，替换当前可见工具集，否则合并"
                )
        ));
        cnSchema.put("required", java.util.List.of("tool_names"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enToolNames = new LinkedHashMap<>();
        enToolNames.put("type", "array");
        enToolNames.put("items", Map.of("type", "string"));
        enToolNames.put("description", "Names of tools to make visible for the current session");
        enSchema.put("properties", Map.of(
                "tool_names", enToolNames,
                "replace", Map.of(
                        "type", "boolean",
                        "description", "If true, replace the current visible tool set instead of merging"
                )
        ));
        enSchema.put("required", java.util.List.of("tool_names"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "load_tools";
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
