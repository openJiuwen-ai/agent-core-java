/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual tool description and input params for SearchTools tool.
 * <p>
 * Mirrors Python's {@code SearchToolsMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.search_tools}.
 */
public class SearchToolsMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "根据能力、名称、描述或参数提示搜索候选工具。仅用于发现，不会直接调用工具。");
        DESCRIPTIONS.put("en", "Search candidate tools by capability, name, description, "
                + "or parameter hints. Discovery only; tools are not directly callable.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("query", Map.of("type", "string", "description", "搜索候选工具的查询文本"));
        cnProps.put("limit", Map.of("type", "integer", "description", "返回候选工具的最大数量"));
        cnProps.put("detail_level", Map.of("type", "integer", "description", "1=name+描述, 2=+参数摘要, 3=+完整参数"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("query"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("query", Map.of("type", "string", "description", "Search query for finding relevant candidate tools"));
        enProps.put("limit", Map.of("type", "integer", "description", "Maximum number of candidate tools to return"));
        enProps.put("detail_level", Map.of("type", "integer", "description", "1=name+description, 2=+parameter summary, 3=+full parameters"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("query"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "search_tools";
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