/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for read_memory tool.
 * <p>
 * Mirrors Python's {@code ReadMemoryMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.memory}.
 */
public class ReadMemoryMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "按 offset/limit 读取 memory/ 下记忆文件的部分内容（用于分页阅读）。");
        DESCRIPTIONS.put("en", "Read a portion of a memory file under memory/ using offset/limit (for paging).");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("path", Map.of("type", "string", "description", "memory/ 下的目标文件路径（相对路径）"));
        cnProps.put("offset", Map.of("type", "integer", "description", "起始偏移（可选）"));
        cnProps.put("limit", Map.of("type", "integer", "description", "读取条数限制（可选）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("path"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("path", Map.of("type", "string", "description", "Target path under memory/ (relative path)"));
        enProps.put("offset", Map.of("type", "integer", "description", "Starting offset (optional)"));
        enProps.put("limit", Map.of("type", "integer", "description", "Limit on number of items to read (optional)"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("path"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "read_memory";
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