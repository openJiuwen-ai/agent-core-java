/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual description and input params for the PowerShell tool.
 * <p>
 * Mirrors Python's {@code PowerShellMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.powershell}.
 */
public class PowerShellMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn",
                "执行给定的 PowerShell 命令并返回输出。工作目录会在命令之间保持不变。"
                + "重要：本工具用于通过 PowerShell 执行终端操作（git、npm、docker、python 等）。"
                + "优先使用 glob、grep、read_file、edit_file、write_file。"
                + "PowerShell 版本兼容：默认按 Windows PowerShell 5.1 兼容方式编写命令。"
                + "条件串联使用 `A; if ($?) { B }`，无条件顺序执行用 `A; B`。");
        DESCRIPTIONS.put("en",
                "Execute PowerShell commands and return output. Working directory persists between commands. "
                + "Important: Use this tool for terminal operations (git, npm, docker, python, PowerShell cmdlets). "
                + "Prefer glob, grep, read_file, edit_file, write_file for file operations. "
                + "PowerShell version compatibility: Default to Windows PowerShell 5.1 compatible syntax. "
                + "Use `A; if ($?) { B }` for conditional chaining, `A; B` for unconditional sequential execution.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("command", Map.of("type", "string", "description", "要执行的 PowerShell 命令"));
        cnProps.put("timeout", Map.of("type", "integer", "description", "超时时间（毫秒）"));
        cnProps.put("run_in_background", Map.of("type", "boolean", "description", "是否后台运行"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("command"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("command", Map.of("type", "string", "description", "The PowerShell command to execute"));
        enProps.put("timeout", Map.of("type", "integer", "description", "Timeout in milliseconds"));
        enProps.put("run_in_background", Map.of("type", "boolean", "description", "Run command in background"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("command"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "powershell";
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