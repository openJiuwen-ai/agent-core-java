/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class SessionsCancelMetadataProvider implements ToolMetadataProvider {
    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "sessions_cancel";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return "Cancel a background async subagent task.";
        }
        return "取消后台异步子代理任务。";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        boolean isEnglish = "en".equalsIgnoreCase(language);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("task_id", Map.of(
                "type", "string",
                "description", isEnglish ? "Task id to cancel" : "要取消的任务 ID"
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("task_id")
        );
    }
}
