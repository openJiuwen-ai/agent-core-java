/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for memory_get tool.
 * <p>
 * Mirrors Python's {@code MemoryGetMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.memory}.
 */
public class MemoryGetMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "按行号切片读取 memory/ 下的记忆 Markdown 文件内容（from_line + lines）。");
        DESCRIPTIONS.put("en", "Read a slice of a memory markdown file under memory/ (from_line + lines).");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("path", Map.of("type", "string", "description", "memory/ 下的目标文件路径（相对路径）"));
        cnProps.put("from_line", Map.of("type", "integer", "description", "起始行号（可选）"));
        cnProps.put("lines", Map.of("type", "integer", "description", "读取行数（可选）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("path"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("path", Map.of("type", "string", "description", "Target path under memory/ (relative path)"));
        enProps.put("from_line", Map.of("type", "integer", "description", "Starting line number (optional)"));
        enProps.put("lines", Map.of("type", "integer", "description", "Number of lines to read (optional)"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("path"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "memory_get";
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