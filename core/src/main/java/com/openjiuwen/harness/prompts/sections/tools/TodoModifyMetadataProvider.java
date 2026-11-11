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
    private static final String TODO_MODIFY_DESCRIPTION_CN = """
            修改当前会话的待办事项。支持批量操作，尽量将多个变更合并为一次调用。

            核心用途：更新（update）、删除（delete）、取消（cancel）、追加（append）、\
            在其后插入（insert_after）、在其前插入（insert_before）

            重要说明：
            - 若需重新规划整个任务列表，请调用 todo_create
            - 支持批量操作，尽量将多个变更合并为一次调用，避免连续多次调用
            - **id 使用你在 todo_create 时自定义的语义 ID**（如 "translate_doc"），不要使用 UUID

            action 支持的操作类型：

            update：修改现有任务的状态或标题（id 不可修改，支持部分字段更新）：
                {
                    "action": "update",
                    "todos": [
                        {"id": "translate_doc", "status": "completed"},
                        {"id": "analyze_code", "status": "in_progress"}
                    ]
                }

            支持修改 selected_model_id：若任务 selected_model_id 不为空，且执行结果质量不佳（输出不准确、\
            逻辑错误、未达预期），应根据模型描述更新质量更高的模型ID：
                {
                    "action": "update",
                    "todos": [
                        {"id": "translate_doc", "selected_model_id": "smart", "status": "pending"}
                    ]
                }

            cancel：将指定任务标记为 cancelled（任务将被忽略，不再执行）：
                {
                    "action": "cancel",
                    "ids": ["translate_doc", "analyze_code"]
                }

            delete：从列表中永久删除指定任务：
                {
                    "action": "delete",
                    "ids": ["translate_doc"]
                }

            append：在列表末尾追加新任务（id 由你指定，须简短语义且唯一）：
                {
                    "action": "append",
                    "todos": [
                        {"id": "write_report", "content": "新任务内容", "activeForm": "执行新任务", \
            "description": "任务的详细描述", "status": "pending"}
                    ]
                }

            insert_after：在指定任务之后插入新任务（目标任务状态须为 in_progress 或 pending）：
                {
                    "action": "insert_after",
                    "todo_data": {"target_id": "translate_doc", "items": [{"id": "review_translation", \
            "content": "插入的任务", "activeForm": "执行插入的任务", "description": "任务的详细描述", "status": \
            "pending", "selected_model_id": "fast"}]}
                }

            insert_before：在指定任务之前插入新任务（目标任务状态须为 pending）：
                {
                    "action": "insert_before",
                    "todo_data": {"target_id": "analyze_code", "items": [{"id": "setup_env", "content": \
            "插入的任务", "activeForm": "执行插入的任务", "description": "任务的详细描述", "status": \
            "pending"}]}
                }

            核心规则：
            - 同一时间只能有一个任务处于 in_progress 状态
            - update 操作：id 字段不可修改，其他字段支持部分更新
            - insert_after：目标任务状态必须为 in_progress 或 pending
            - insert_before：目标任务状态必须为 pending
            - 如果任务的 selected_model_id 为空时，任何操作都不要更改 selected_model_id 字段
            """;

    private static final String TODO_MODIFY_DESCRIPTION_EN = """
            Modify todo items for the current session. Supports batch operations — consolidate multiple changes \
            into a single call whenever possible.

            Core purpose: update, delete, cancel, append, insert_after, insert_before.

            Important notes:
            - To re-plan the entire task list, call todo_create instead
            - Batch multiple changes into one call; avoid calling todo_modify repeatedly in succession
            - **Use the semantic id you assigned in todo_create** (e.g. "translate_doc"); never use UUIDs

            Supported action types:

            update: Modify status or content of existing tasks (id cannot be changed; partial field updates \
            supported):
                {
                    "action": "update",
                    "todos": [
                        {"id": "translate_doc", "status": "completed"},
                        {"id": "analyze_code", "status": "in_progress"}
                    ]
                }

            Support modifying selected_model_id: If the task's selected_model_id is not empty and the execution \
            result is of poor quality (inaccurate output, logical errors, or failure to meet expectations), the \
            model ID should be updated according to the model description to a higher-quality model:
                {
                    "action": "update",
                    "todos": [
                        {"id": "translate_doc", "selected_model_id": "smart", "status": "pending"}
                    ]
                }

            cancel: Mark specified tasks as cancelled (tasks will be ignored and not executed):
                {
                    "action": "cancel",
                    "ids": ["translate_doc", "analyze_code"]
                }

            delete: Permanently remove specified tasks from the list:
                {
                    "action": "delete",
                    "ids": ["translate_doc"]
                }

            append: Add new tasks at the end of the list (id must be a short semantic string you choose, unique \
            within the session):
                {
                    "action": "append",
                    "todos": [
                        {"id": "write_report", "content": "New task content", "activeForm": "Executing new \
            task", "description": "Detailed description of the task", "status": "pending"}
                    ]
                }

            insert_after: Insert new tasks after the specified task (target must be in_progress or pending):
                {
                    "action": "insert_after",
                    "todo_data": {"target_id": "translate_doc", "items": [{"id": "review_translation", \
            "content": "Inserted task", "activeForm": "Executing inserted task", "description": "Detailed \
            description of the task", "status": "pending", "selected_model_id": "fast"}]}
                }

            insert_before: Insert new tasks before the specified task (target must be pending):
                {
                    "action": "insert_before",
                    "todo_data": {"target_id": "analyze_code", "items": [{"id": "setup_env", "content": \
            "Inserted task", "activeForm": "Executing inserted task", "description": "Detailed description of \
            the task", "status": "pending"}]}
                }

            Core rules:
            - Only one task can be in_progress at a time
            - update action: id field cannot be modified; other fields support partial updates
            - insert_after: target task status must be in_progress or pending
            - insert_before: target task status must be pending
            - If the task's selected_model_id is empty, do not modify the selected_model_id field in any \
            operation.
            """;

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "todo_modify";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription(String language) {
        return text(language, TODO_MODIFY_DESCRIPTION_CN, TODO_MODIFY_DESCRIPTION_EN);
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
                text(language, "update：每项用 id 指定待修改任务，其他字段按需提供；append：每项包含新任务完整信息",
                        "update: id identifies each task with optional fields to change; "
                                + "append: complete new todo objects")));
        properties.put("todo_data", insertionSchema(language));
        return ToolSchemaSupport.objectSchema(properties, List.of("action"));
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
