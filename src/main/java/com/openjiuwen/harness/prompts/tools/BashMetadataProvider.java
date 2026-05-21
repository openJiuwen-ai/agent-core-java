/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual tool description and input params for Bash tool.
 * <p>
 * Mirrors Python's {@code BashMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.bash}.
 */
public class BashMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn",
                "执行 Shell 命令并返回输出。\n"
                + "工作目录在命令之间保持不变。优先使用专用工具而非 bash。\n"
                + "使用说明：路径含空格用双引号；可用 timeout 参数；run_in_background 可后台运行。");
        DESCRIPTIONS.put("en",
                "Execute shell commands and return output.\n"
                + "Working directory persists between commands. Prefer dedicated tools over bash.\n"
                + "Usage: quote paths with spaces; set timeout; use run_in_background for async.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("command", Map.of("type", "string", "description", "要执行的 shell 命令"));
        cnProps.put("timeout", Map.of("type", "integer", "description", "超时时间（毫秒）"));
        cnProps.put("run_in_background", Map.of("type", "boolean", "description", "是否后台运行"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("command"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("command", Map.of("type", "string", "description", "The shell command to execute"));
        enProps.put("timeout", Map.of("type", "integer", "description", "Timeout in milliseconds"));
        enProps.put("run_in_background", Map.of("type", "boolean", "description", "Run command in background"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("command"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "bash";
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
