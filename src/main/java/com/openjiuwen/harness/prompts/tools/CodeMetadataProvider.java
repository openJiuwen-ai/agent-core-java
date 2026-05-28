/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual tool description and input params for Code execution tool.
 * <p>
 * Mirrors Python's {@code CodeMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.code}.
 */
public class CodeMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "执行代码（Python 或 JavaScript）。");
        DESCRIPTIONS.put("en", "Execute code (Python or JavaScript).");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("code", Map.of("type", "string", "description", "要执行的代码"));
        cnProps.put("language", Map.of("type", "string", "description", "编程语言，支持 python 或 javascript，默认 python"));
        cnProps.put("timeout", Map.of("type", "integer", "description", "超时时间（秒），默认 300，上限 3600"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("code"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("code", Map.of("type", "string", "description", "Code to execute"));
        enProps.put("language", Map.of("type", "string", "description", "Programming language, supports python or javascript, default python"));
        enProps.put("timeout", Map.of("type", "integer", "description", "Timeout in seconds, default 300, max 3600"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("code"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "code";
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