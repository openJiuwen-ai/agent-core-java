/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual description and input params for sessions_cancel tool.
 * <p>
 * Mirrors Python's {@code SessionsCancelMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.session_tools}.
 */
public class SessionsCancelMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "取消运行中的后台任务。使用 all=true 可取消所有任务。");
        DESCRIPTIONS.put("en", "Cancel running background task(s). Use all=true to cancel ALL tasks.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("task_id", Map.of("type", "string", "description", "要取消的任务 ID（当 all=false 时必填）"));
        cnProps.put("all", Map.of("type", "boolean", "description", "取消所有任务（默认 false）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.emptyList());
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("task_id", Map.of("type", "string", "description", "Task ID to cancel (required if all=false)"));
        enProps.put("all", Map.of("type", "boolean", "description", "Cancel all tasks (default false)"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.emptyList());
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "sessions_cancel";
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