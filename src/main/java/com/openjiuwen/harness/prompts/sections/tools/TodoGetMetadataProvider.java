/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Metadata provider for fetching a single todo item by id.
 *
 * @since 0.1.12
 */
public final class TodoGetMetadataProvider implements ToolMetadataProvider {
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "todo_get";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language,
                "根据任务 ID 获取单个任务的完整详情。",
                "Get full details of a single task by id.");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[] {
                        "session_id", ToolSchemaSupport.property("string", ToolSchemaSupport.localized(language,
                                "会话 ID，默认当前会话", "Session id, defaults to current session")),
                        "id", ToolSchemaSupport.property("string", ToolSchemaSupport.localized(language,
                                "任务唯一标识符", "Unique task identifier"))
                }),
                List.of("id")
        );
    }
}
