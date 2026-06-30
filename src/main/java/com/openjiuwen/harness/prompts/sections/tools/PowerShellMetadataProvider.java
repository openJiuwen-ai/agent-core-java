/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * PowerShell tool metadata provider.
 *
 * @since 0.1.12
 */
public final class PowerShellMetadataProvider implements ToolMetadataProvider {
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "powershell";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language,
                "执行给定的 PowerShell 命令并返回输出。工作目录会在命令之间保持不变；但 shell 状态不会保留。",
                "Execute a given PowerShell command and return its output. The working directory persists between "
                        + "commands; shell state does not.");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[] {
                        "command", ToolSchemaSupport.property("string", text(language,
                                "要执行的 PowerShell 命令", "PowerShell command to execute")),
                        "timeout", ToolSchemaSupport.property("integer", text(language,
                                "可选超时时间（秒），默认 300，上限 3600",
                                "Optional timeout in seconds, default 300, max 3600")),
                        "workdir", ToolSchemaSupport.property("string", text(language,
                                "执行目录（相对或绝对路径），默认工作区根目录；不能越出沙箱",
                                "Working directory, relative or absolute, defaults to workspace root")),
                        "background", ToolSchemaSupport.property("boolean", text(language,
                                "是否后台运行，默认 false；设为 true 时立即返回 PID",
                                "Run in background, default false; returns PID immediately when true")),
                        "max_output_chars", ToolSchemaSupport.property("integer", text(language,
                                "最大输出字符数，默认 8000，最大 20000",
                                "Max output characters, default 8000, max 20000")),
                        "description", ToolSchemaSupport.property("string", text(language,
                                "命令描述（可选），用于日志和审计",
                                "Optional command description for logging and audit trail"))
                }),
                List.of("command")
        );
    }

    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
