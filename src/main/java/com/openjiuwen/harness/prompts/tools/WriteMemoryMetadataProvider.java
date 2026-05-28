/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for write_memory tool.
 * <p>
 * Mirrors Python's {@code WriteMemoryMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.memory}.
 */
public class WriteMemoryMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "写入记忆内容到 memory/ 下的 Markdown 文件；支持覆盖写或追加写（append）。");
        DESCRIPTIONS.put("en", "Write memory content to a markdown file under memory/; supports overwrite or append.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("path", Map.of("type", "string", "description", "memory/ 下的目标文件路径（相对路径）"));
        cnProps.put("content", Map.of("type", "string", "description", "要写入的记忆内容"));
        cnProps.put("append", Map.of("type", "boolean", "description", "是否追加写（默认覆盖）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Arrays.asList("path", "content"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("path", Map.of("type", "string", "description", "Target path under memory/ (relative path)"));
        enProps.put("content", Map.of("type", "string", "description", "Memory content to write"));
        enProps.put("append", Map.of("type", "boolean", "description", "Append mode (default overwrite)"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Arrays.asList("path", "content"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "write_memory";
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