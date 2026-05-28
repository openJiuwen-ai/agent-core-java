/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for todo_create tool.
 * <p>
 * Mirrors Python's {@code TodoCreateMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.todo}.
 */
public class TodoCreateMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn",
                "创建当前会话的待办事项列表，用于跟踪进度、组织复杂任务。"
                + "何时使用：任务需要 3+ 步骤、用户提供多个待完成事项、用户明确要求待办清单。"
                + "何时不使用：单个简单任务、纯信息查询、可在 3 步以内完成的琐碎任务。"
                + "规则：第一个任务自动设为 in_progress，同一时间只能有一个 in_progress。");
        DESCRIPTIONS.put("en",
                "Create a todo list for the current session to track progress and organize complex tasks. "
                + "When to Use: Task requires 3+ steps, user provides multiple items, user requests todo list. "
                + "When NOT to Use: Single straightforward task, pure informational queries, completable in <3 steps. "
                + "Rules: First task auto set to in_progress, only one in_progress at a time.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("todos", Map.of("type", "array", "description", "任务列表 JSON 数组"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("todos"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("todos", Map.of("type", "array", "description", "Todo list JSON array"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("todos"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "todo_create";
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