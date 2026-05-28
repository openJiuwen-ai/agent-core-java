/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for memory_search tool.
 * <p>
 * Mirrors Python's {@code MemorySearchMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.memory}.
 */
public class MemorySearchMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "在长期记忆中检索过往信息（决策、偏好、人物、日期、TODO 等），返回相关片段与引用线索。");
        DESCRIPTIONS.put("en", "Search long-term memory (prior decisions, preferences, people, dates, todos) "
                + "and return relevant snippets and references.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("query", Map.of("type", "string", "description", "检索关键词或问题"));
        cnProps.put("max_results", Map.of("type", "integer", "description", "最多返回条数（可选）"));
        cnProps.put("min_score", Map.of("type", "number", "description", "最小相关度阈值（可选）"));
        cnProps.put("session_key", Map.of("type", "string", "description", "会话键（可选，用于上下文隔离/过滤）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("query"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("query", Map.of("type", "string", "description", "Search query string"));
        enProps.put("max_results", Map.of("type", "integer", "description", "Maximum number of results (optional)"));
        enProps.put("min_score", Map.of("type", "number", "description", "Minimum relevance score threshold (optional)"));
        enProps.put("session_key", Map.of("type", "string", "description", "Session key (optional, for scoping/filtering)"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("query"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "memory_search";
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