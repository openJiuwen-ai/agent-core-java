/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual description and input params for sessions_output tool.
 * <p>
 * Mirrors Python's {@code SessionsOutputMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.session_tools}.
 */
public class SessionsOutputMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "获取后台任务的输出结果。系统会在任务完成时发送通知，所以阻塞等待很少需要。");
        DESCRIPTIONS.put("en", "Get output from background task. System notifies on completion, so blocking wait rarely needed.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("task_id", Map.of("type", "string", "description", "要获取输出的任务 ID"));
        cnProps.put("block", Map.of("type", "boolean", "description", "是否阻塞等待完成（默认 false）"));
        cnProps.put("timeout", Map.of("type", "integer", "description", "最大等待时间（毫秒，默认60000，最大600000）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("task_id"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("task_id", Map.of("type", "string", "description", "Task ID to get output from"));
        enProps.put("block", Map.of("type", "boolean", "description", "Block wait for completion (default false)"));
        enProps.put("timeout", Map.of("type", "integer", "description", "Max wait time in ms (default 60000, max 600000)"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("task_id"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "sessions_output";
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