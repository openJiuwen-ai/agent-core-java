/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TodoCreateMetadataProvider.
 * 
 * @since 0.1.7
 */
public final class TodoCreateMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "todo_create";
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
            return "Create a todo list for the current session to track progress.";
        }
        return "创建当前会话的待办事项列表，用于跟踪进度。";
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
        properties.put("session_id", Map.of("type", "string", "description",
                isEnglish ? "Session id, defaults to current session" : "会话 ID，默认当前会话"));
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("content", Map.of("type", "string", "description", isEnglish ? "Task summary" : "任务摘要"));
        itemProperties.put("activeForm",
                Map.of("type", "string", "description", isEnglish ? "Present-tense form of content" : "content 的进行语态"));
        itemProperties.put("description",
                Map.of("type", "string", "description", isEnglish ? "Detailed task description" : "任务详细描述"));
        itemProperties.put("selected_model_id", Map.of("type", "string", "description",
                isEnglish ? "Optional model id selected for this task" : "可选的任务执行模型 ID"));
        itemProperties.put("depends_on", Map.of("type", "array", "items", Map.of("type", "string"), "description",
                isEnglish ? "Prerequisite task ids" : "前置任务 ID 列表"));
        itemProperties.put("result_summary",
                Map.of("type", "string", "description", isEnglish ? "Optional result summary" : "可选的执行结果摘要"));
        itemProperties.put("meta_data",
                Map.of("type", "object", "description", isEnglish ? "Additional task metadata" : "额外任务元数据"));
        properties.put("tasks",
                Map.of("type", "array", "items",
                        Map.of("type", "object", "properties", itemProperties, "required",
                                List.of("content", "activeForm", "description")),
                        "description", isEnglish ? "Ordered todo task objects" : "按顺序排列的任务对象"));
        return Map.of("type", "object", "properties", properties, "required", List.of("tasks"));
    }
}
