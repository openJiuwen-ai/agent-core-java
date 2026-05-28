/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for todo_modify tool.
 * <p>
 * Mirrors Python's {@code TodoModifyMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.todo}.
 */
public class TodoModifyMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn",
                "修改当前会话的待办事项列表状态。支持：追加任务、标记完成、取消任务、更新优先级。"
                + "状态值：pending、in_progress、completed、cancelled。同一时间只能有一个 in_progress。");
        DESCRIPTIONS.put("en",
                "Modify the current session's todo list status. Supports: append tasks, mark complete, cancel, update priority. "
                + "Status values: pending, in_progress, completed, cancelled. Only one in_progress at a time.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("todos", Map.of("type", "array", "description", "更新后的任务列表"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("todos"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("todos", Map.of("type", "array", "description", "Updated todo list"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("todos"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "todo_modify";
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