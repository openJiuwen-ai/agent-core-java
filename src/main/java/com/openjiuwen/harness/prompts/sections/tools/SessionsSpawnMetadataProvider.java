/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SessionsSpawnMetadataProvider.
 * 
 * @since 0.1.7
 */
public final class SessionsSpawnMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "sessions_spawn";
    }

    /**
     * getDescription.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDescription(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return "Create an async background subagent task and return immediately.";
        }
        return "创建异步后台子代理任务，并立即返回。";
    }

    /**
     * getInputParams.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getInputParams(String language) {
        boolean isEnglish = "en".equalsIgnoreCase(language);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subagent_type", Map.of("type", "string", "description", isEnglish ? "Subagent type" : "子代理类型"));
        properties.put("task_description",
                Map.of("type", "string", "description", isEnglish ? "Task description" : "任务描述"));
        properties.put("parent_session_id", Map.of("type", "string", "description",
                isEnglish ? "Parent session id for correlation" : "用于关联的父会话 ID"));
        return Map.of("type", "object", "properties", properties, "required",
                List.of("subagent_type", "task_description"));
    }
}
