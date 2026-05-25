/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for edit_memory tool.
 * <p>
 * Mirrors Python's {@code EditMemoryMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.memory}.
 */
public class EditMemoryMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "在 memory/ 下的记忆文件中做精确字符串替换（old_text → new_text）。");
        DESCRIPTIONS.put("en", "Perform an exact string replacement inside a memory file (old_text → new_text).");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("path", Map.of("type", "string", "description", "memory/ 下的目标文件路径（相对路径）"));
        cnProps.put("old_text", Map.of("type", "string", "description", "要替换的旧文本"));
        cnProps.put("new_text", Map.of("type", "string", "description", "替换后的新文本"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Arrays.asList("path", "old_text", "new_text"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("path", Map.of("type", "string", "description", "Target path under memory/ (relative path)"));
        enProps.put("old_text", Map.of("type", "string", "description", "Old text to replace"));
        enProps.put("new_text", Map.of("type", "string", "description", "New text to replace with"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Arrays.asList("path", "old_text", "new_text"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "edit_memory";
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