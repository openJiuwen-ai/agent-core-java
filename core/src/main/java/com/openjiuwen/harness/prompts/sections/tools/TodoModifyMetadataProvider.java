/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定义待办修改工具的统一参数结构。
 *
 * @since 0.1.7
 */
public final class TodoModifyMetadataProvider implements ToolMetadataProvider {
    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "todo_modify";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription(String language) {
        return text(language,
                "修改当前会话的待办事项。必须指定 action。update 使用 todos 数组，"
                        + "每项用 id 定位任务，status、result_summary 等修改字段必须放在该任务对象内，不能放在顶层。"
                        + "示例：{\"action\":\"update\",\"todos\":[{\"id\":\"task-1\","
                        + "\"status\":\"completed\",\"result_summary\":\"已完成\"}]}。"
                        + "delete/cancel 使用 ids；append 使用 todos；insert_after/insert_before 使用 todo_data。"
                        + "新增或插入任务必须提供 id、content、activeForm、description、status。",
                "Modify the current session's todos. action is required. For update, use todos: each item has id "
                        + "and the fields to change. status and result_summary belong INSIDE each todo, "
                        + "never at the root. "
                        + "Example: {\"action\":\"update\",\"todos\":[{\"id\":\"task-1\","
                        + "\"status\":\"completed\",\"result_summary\":\"done\"}]}. "
                        + "delete/cancel use ids; append uses todos; insert_after/insert_before use todo_data. "
                        + "New or inserted todos require id, content, activeForm, description and status.");
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getInputParams(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("session_id", ToolSchemaSupport.property("string",
                text(language, "会话 ID，默认当前会话", "Session id, defaults to current session")));
        properties.put("action", ToolSchemaSupport.enumProperty("string",
                List.of("update", "delete", "cancel", "append", "insert_after", "insert_before"),
                text(language, "必填的操作类型", "Required operation type")));
        properties.put("ids", Map.of("type", "array", "items", Map.of("type", "string"), "description",
                text(language, "仅用于 delete/cancel 的非空任务 ID 列表", "Non-empty task ids for delete/cancel only")));
        properties.put("todos", Map.of("type", "array", "items", todoItemSchema(language), "description",
                text(language, "update：非空数组，每项包含 id 和修改字段；append：每项包含新任务完整信息",
                        "update: non-empty array of id plus fields to change; append: complete new todo objects")));
        properties.put("todo_data", insertionSchema(language));
        Map<String, Object> schema = new LinkedHashMap<>(
                ToolSchemaSupport.objectSchema(properties, List.of("action")));
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> todoItemSchema(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", ToolSchemaSupport.property("string", text(language, "任务 ID", "Todo id")));
        properties.put("content", ToolSchemaSupport.property("string", text(language, "任务内容", "Task content")));
        properties.put("activeForm", ToolSchemaSupport.property("string",
                text(language, "任务进行态", "Present-tense task form")));
        properties.put("description", ToolSchemaSupport.property("string",
                text(language, "任务详细描述", "Detailed task description")));
        properties.put("status", ToolSchemaSupport.enumProperty("string",
                List.of("pending", "in_progress", "completed", "cancelled"),
                text(language, "该任务的新状态", "New status of this todo")));
        properties.put("priority", ToolSchemaSupport.enumProperty("string", List.of("low", "medium", "high"),
                text(language, "任务优先级", "Task priority")));
        properties.put("selected_model_id", ToolSchemaSupport.property("string",
                text(language, "优先使用的模型 ID", "Preferred model id")));
        properties.put("depends_on", Map.of("type", "array", "items", Map.of("type", "string"), "description",
                text(language, "依赖的任务 ID 列表", "Dependent task ids")));
        properties.put("result_summary", ToolSchemaSupport.property("string",
                text(language, "该任务的完成或阶段性结果摘要", "Completion or progress summary of this todo")));
        properties.put("meta_data", Map.of("type", "object", "description",
                text(language, "额外任务元数据", "Additional task metadata")));
        return ToolSchemaSupport.objectSchema(properties, List.of("id"));
    }

    private Map<String, Object> insertionSchema(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("target_id", ToolSchemaSupport.property("string",
                text(language, "用于定位插入位置的已有任务 ID", "Existing todo id locating the insertion position")));
        properties.put("items", Map.of("type", "array", "items", todoItemSchema(language), "description",
                text(language, "要插入的完整新任务列表", "Complete new todos to insert")));
        Map<String, Object> schema = new LinkedHashMap<>(
                ToolSchemaSupport.objectSchema(properties, List.of("target_id", "items")));
        schema.put("description", text(language, "仅用于 insert_after/insert_before",
                "For insert_after/insert_before only"));
        return schema;
    }

    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
