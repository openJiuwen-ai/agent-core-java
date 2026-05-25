/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual description and input params for sessions_spawn tool.
 * <p>
 * Mirrors Python's {@code SessionsSpawnMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.session_tools}.
 */
public class SessionsSpawnMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn",
                "创建异步后台子任务，立即返回待处理状态，任务在后台执行不阻塞当前对话。"
                + "何时使用：复杂多步骤任务、需要并行处理、需要沙箱执行、只需最终输出、想继续处理用户请求时。"
                + "何时不使用：简单快速任务、需要观察中间步骤、分解无益、用户明确想看过程时。");
        DESCRIPTIONS.put("en",
                "Create async background subagent task that returns pending status immediately "
                + "while the task executes in the background without blocking the current conversation. "
                + "When to use: Complex multi-step tasks, parallel processing, sandboxed execution, only final output needed. "
                + "When NOT to use: Simple quick tasks, need intermediate steps, decomposition adds latency.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("subagent_type", Map.of("type", "string", "description", "子代理类型"));
        cnProps.put("task_description", Map.of("type", "string", "description", "任务描述"));
        cnProps.put("prompt", Map.of("type", "string", "description", "完整任务提示"));
        cnProps.put("run_in_background", Map.of("type", "boolean", "description", "是否后台运行（默认 true）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Arrays.asList("subagent_type", "task_description"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("subagent_type", Map.of("type", "string", "description", "Subagent type"));
        enProps.put("task_description", Map.of("type", "string", "description", "Task description"));
        enProps.put("prompt", Map.of("type", "string", "description", "Full task prompt"));
        enProps.put("run_in_background", Map.of("type", "boolean", "description", "Run in background (default true)"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Arrays.asList("subagent_type", "task_description"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "sessions_spawn";
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