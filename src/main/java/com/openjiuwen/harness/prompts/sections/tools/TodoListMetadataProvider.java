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
public final class TodoListMetadataProvider implements ToolMetadataProvider {
    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "todo_list";
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
            return "Retrieve active todo items for the current session.";
        }
        return "检索当前会话的活跃待办事项。";
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
        properties.put("session_id", Map.of(
                "type", "string",
                "description", isEnglish ? "Session id, defaults to current session" : "会话 ID，默认当前会话"
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of()
        );
    }
}
