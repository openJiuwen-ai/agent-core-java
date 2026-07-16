/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TaskMetadataProvider.
 * 
 * @since 0.1.7
 */
public final class TaskMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "task_tool";
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
            return "Launch an ephemeral subagent to handle complex, multi-step independent tasks with"
                    + " isolated context windows.";
        }
        return "启动临时子代理，处理复杂、多步骤、独立的隔离上下文任务。";
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
        properties.put("subagent_type",
                Map.of("type", "string", "description", isEnglish ? "Type of subagent to use" : "子代理类型"));
        properties.put("task_description",
                Map.of("type", "string", "description", isEnglish ? "Task description" : "任务描述"));
        properties.put("parent_session_id", Map.of("type", "string", "description",
                isEnglish ? "Parent session id for correlation" : "用于关联的父会话 ID"));
        return Map.of("type", "object", "properties", properties, "required",
                List.of("subagent_type", "task_description"));
    }
}
