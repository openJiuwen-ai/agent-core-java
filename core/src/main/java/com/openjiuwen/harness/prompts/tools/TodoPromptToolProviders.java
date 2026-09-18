/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.harness.prompts.tools.todo} in
 * {@code openjiuwen/harness/prompts/tools/todo.py}.
 */
public final class TodoPromptToolProviders {

    private static final String TODO_CREATE_DESCRIPTION_CN = """
            创建当前会话的待办事项列表，用于跟踪进度、组织复杂任务，帮助用户了解整体执行情况。

            ## 何时使用

            主动在以下场景调用：
            - 任务需要 3 个或更多步骤
            - 用户提供多个待完成事项（编号列表、逗号或分号分隔）
            - 用户明确要求使用待办清单
            - 任务具有规划性质（多步骤实现、功能开发等）

            识别到规划需求后，立即调用本工具。

            ## 何时不使用

            - 单个简单任务
            - 纯信息查询或对话
            - 可在 3 步以内完成的琐碎任务

            ## 使用方式

            入参为 JSON 数组（每个任务必须包含 id 字段）：
                {"tasks": [{"id": "translate_doc", "content": "翻译文档", "activeForm": "正在翻译文档", "description": "将文档翻译为目标语言", "selected_model_id": "fast"}, {"id": "analyze_arch", "content": "分析代码架构", "activeForm": "正在分析代码架构", "description": "梳理代码模块结构与依赖关系", "selected_model_id": "smart"}]}

            ## 规则

            - **id 字段为必填**：必须为每个任务指定简短、语义清晰的唯一字符串 ID（如 "translate_doc"、"analyze_code"），禁止使用随机字符或 UUID。同一会话内 ID 不得重复。此 ID 在后续 todo_modify 中用于精准定位任务，是跨轮次更新状态的唯一依据
            - 第一个任务自动设为 in_progress，其余为 pending
            - 同一时间只能有一个 in_progress 任务
            - 任务描述必须具体、可执行、清晰明确
            - 调用本工具会覆盖当前会话的任务列表；若需追加任务，请使用 todo_modify
            - 当没有获取到当前可用模型信息时，不要添加 selected_model_id 字段；否则必须添加 selected_model_id 指定执行任务的模型 ID
            """;

    private static final String TODO_CREATE_DESCRIPTION_EN = """
            Create a todo list for the current session to track progress, organize complex tasks, and help the user understand overall execution status.

            ## When to Use

            Call proactively in these scenarios:
            - Task requires 3 or more distinct steps
            - User provides multiple items to complete (numbered list, comma- or semicolon-separated)
            - User explicitly requests a todo list
            - Task has planning nature (multi-step implementation, feature development, etc.)

            Once you identify a planning need, call this tool immediately.

            ## When NOT to Use

            - Single, straightforward task
            - Pure informational queries or conversation
            - Tasks completable in fewer than 3 trivial steps

            ## Usage

            Input is a JSON array (each task must include an id field):
                {"tasks": [{"id": "translate_doc", "content": "Translate document", "activeForm": "Translating document", "description": "Translate the document into the target language", "selected_model_id": "fast"}, {"id": "analyze_arch", "content": "Analyze code architecture", "activeForm": "Analyzing code architecture", "description": "Map out module structure and dependencies", "selected_model_id": "smart"}]}

            ## Rules

            - **id is required**: You must provide a short, semantically meaningful unique string ID for each task (e.g. "translate_doc", "analyze_code"). Do NOT use random characters or UUIDs. IDs must be unique within a session. This ID is used by todo_modify to precisely locate tasks and is the sole key for cross-turn status updates
            - First task is automatically set to in_progress, others to pending
            - Only one task can be in_progress at a time
            - Task descriptions must be specific, actionable, and clear
            - Calling this tool replaces the current session's task list; use todo_modify to append tasks
            - When the currently available model information is not obtained, the selected_model_id field should not be added; otherwise, selected_model_id must be added to specify the model ID for executing the task.
            """;

    private static final Map<String, String> TODO_CREATE_DESCRIPTION = Map.of(
            "cn", TODO_CREATE_DESCRIPTION_CN,
            "en", TODO_CREATE_DESCRIPTION_EN
    );

    private static final String TODO_LIST_DESCRIPTION_CN = """
            检索并显示当前会话的所有待办事项。

            ## 何时使用 todo_list（而非 todo_modify）

            使用 todo_list 的场景：
            - 需要查看当前任务全貌和各任务ID，再决定如何更新
            - 不确定当前有哪些任务处于 in_progress 或 pending

            使用 todo_modify 的场景（不需要先调用 todo_list）：
            - 已知任务 ID，直接更新任务信息
            - 任务刚完成，立即标记为 completed
            """;

    private static final String TODO_LIST_DESCRIPTION_EN = """
            Retrieve and display all todo items for the current session

            ## When to Use todo_list (vs. todo_modify)

            Use todo_list when:
            - You need an overview of all tasks and their IDs before deciding how to update
            - You are unsure which tasks are currently in_progress or pending

            Use todo_modify directly (no need to call todo_list first) when:
            - You already know the task ID and want to update task information
            - A task just finished and you want to mark it completed immediately
            """;

    private static final Map<String, String> TODO_LIST_DESCRIPTION = Map.of(
            "cn", TODO_LIST_DESCRIPTION_CN,
            "en", TODO_LIST_DESCRIPTION_EN
    );

    private static final String TODO_MODIFY_DESCRIPTION_CN = """
            修改当前会话的待办事项。支持批量操作，尽量将多个变更合并为一次调用。

            核心用途：更新（update）、删除（delete）、取消（cancel）、追加（append）、在其后插入（insert_after）、在其前插入（insert_before）

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

            支持修改 selected_model_id：若任务 selected_model_id 不为空，且执行结果质量不佳（输出不准确、逻辑错误、未达预期），应根据模型描述更新质量更高的模型ID：
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
                        {"id": "write_report", "content": "新任务内容", "activeForm": "执行新任务", "description": "任务的详细描述", "status": "pending"}
                    ]
                }

            insert_after：在指定任务之后插入新任务（目标任务状态须为 in_progress 或 pending）：
                {
                    "action": "insert_after",
                    "todo_data": {"target_id": "translate_doc", "items": [{"id": "review_translation", "content": "插入的任务", "activeForm": "执行插入的任务", "description": "任务的详细描述", "status": "pending", "selected_model_id": "fast"}]}
                }

            insert_before：在指定任务之前插入新任务（目标任务状态须为 pending）：
                {
                    "action": "insert_before",
                    "todo_data": {"target_id": "analyze_code", "items": [{"id": "setup_env", "content": "插入的任务", "activeForm": "执行插入的任务", "description": "任务的详细描述", "status": "pending"}]}
                }

            核心规则：
            - 同一时间只能有一个任务处于 in_progress 状态
            - update 操作：id 字段不可修改，其他字段支持部分更新
            - insert_after：目标任务状态必须为 in_progress 或 pending
            - insert_before：目标任务状态必须为 pending
            - 如果任务的 selected_model_id 为空时，任何操作都不要更改 selected_model_id 字段
            """;

    private static final String TODO_MODIFY_DESCRIPTION_EN = """
            Modify todo items for the current session. Supports batch operations — consolidate multiple changes into a single call whenever possible.

            Core purpose: update, delete, cancel, append, insert_after, insert_before.

            Important notes:
            - To re-plan the entire task list, call todo_create instead
            - Batch multiple changes into one call; avoid calling todo_modify repeatedly in succession
            - **Use the semantic id you assigned in todo_create** (e.g. "translate_doc"); never use UUIDs

            Supported action types:

            update: Modify status or content of existing tasks (id cannot be changed; partial field updates supported):
                {
                    "action": "update",
                    "todos": [
                        {"id": "translate_doc", "status": "completed"},
                        {"id": "analyze_code", "status": "in_progress"}
                    ]
                }

            Support modifying selected_model_id: If the task's selected_model_id is not empty and the execution result is of poor quality (inaccurate output, logical errors, or failure to meet expectations), the model ID should be updated according to the model description to a higher-quality model:
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

            append: Add new tasks at the end of the list (id must be a short semantic string you choose, unique within the session):
                {
                    "action": "append",
                    "todos": [
                        {"id": "write_report", "content": "New task content", "activeForm": "Executing new task", "description": "Detailed description of the task", "status": "pending"}
                    ]
                }

            insert_after: Insert new tasks after the specified task (target must be in_progress or pending):
                {
                    "action": "insert_after",
                    "todo_data": {"target_id": "translate_doc", "items": [{"id": "review_translation", "content": "Inserted task", "activeForm": "Executing inserted task", "description": "Detailed description of the task", "status": "pending", "selected_model_id": "fast"}]}
                }

            insert_before: Insert new tasks before the specified task (target must be pending):
                {
                    "action": "insert_before",
                    "todo_data": {"target_id": "analyze_code", "items": [{"id": "setup_env", "content": "Inserted task", "activeForm": "Executing inserted task", "description": "Detailed description of the task", "status": "pending"}]}
                }

            Core rules:
            - Only one task can be in_progress at a time
            - update action: id field cannot be modified; other fields support partial updates
            - insert_after: target task status must be in_progress or pending
            - insert_before: target task status must be pending
            - If the task's selected_model_id is empty, do not modify the selected_model_id field in any operation.
            """;

    private static final Map<String, String> TODO_MODIFY_DESCRIPTION = Map.of(
            "cn", TODO_MODIFY_DESCRIPTION_CN,
            "en", TODO_MODIFY_DESCRIPTION_EN
    );

    private static final String TODO_GET_DESCRIPTION_CN = """
            根据任务 ID 获取单个任务的完整详情。

            入参：id（任务唯一标识符）

            返回：完整的任务信息，包括 id、content（任务摘要）、activeForm、description（任务详细内容）、status、depends_on、result_summary、meta_data、selected_model_id。
            """;

    private static final String TODO_GET_DESCRIPTION_EN = """
            Get full details of a single task by its ID.

            Input: id (unique task identifier)

            Returns: complete task info including id, content (task summary), activeForm, description (detailed content), status, depends_on, result_summary, meta_data, selected_model_id.
            """;

    private static final Map<String, String> TODO_GET_DESCRIPTION = Map.of(
            "cn", TODO_GET_DESCRIPTION_CN,
            "en", TODO_GET_DESCRIPTION_EN
    );

    private static final Map<String, Map<String, String>> TODO_CREATE_PARAMS = Map.of(
            "tasks", Map.of(
                    "cn",
                    """
                    子任务列表，JSON 数组格式。每个元素为任务对象，必填字段：
                    - id：任务唯一标识符，由你自行指定，必须简短且语义清晰（如 "translate_doc"、"analyze_code"），禁止使用随机字符或 UUID；同一会话内 ID 不得重复；后续 todo_modify 按此 ID 精准定位任务
                    - content：任务摘要描述
                    - activeForm：content 的进行语态（如 content 为「翻译文档」，activeForm 为「正在翻译文档」）
                    - description：任务详细内容
                    可选字段：
                    - selected_model_id：执行任务的模型 ID，见系统提示词「模型选择策略」
                    """,
                    "en",
                    """
                    List of subtasks in JSON array format. Each element is a task object with required fields:
                    - id: unique task identifier that YOU provide; must be short and semantically meaningful (e.g. 'translate_doc', 'analyze_code'); do NOT use random chars or UUIDs; IDs must be unique within a session; todo_modify uses this ID to locate tasks precisely
                    - content: task summary description
                    - activeForm: present-tense form of content (e.g., content 'Translate document' -> activeForm 'Translating document')
                    - description: detailed task content
                    Optional field:
                    - selected_model_id: model ID, see 'Model Selection Strategy' in system prompt
                    """
            )
    );

    private static final Map<String, Map<String, String>> TODO_ITEM_PARAMS = Map.of(
            "id", Map.of(
                    "cn", "任务唯一标识符，由你自行指定的简短语义字符串（如 \"translate_doc\"），禁止使用 UUID，同一会话内须唯一",
                    "en", "Unique task identifier — a short semantic string you provide (e.g. 'translate_doc'); do NOT use UUIDs; must be unique within a session"
            ),
            "content", Map.of("cn", "任务摘要描述", "en", "Task summary description"),
            "activeForm", Map.of("cn", "content 的进行语态", "en", "Present-tense form of content"),
            "description", Map.of("cn", "任务详细内容", "en", "Detailed task content"),
            "status", Map.of("cn", "任务状态", "en", "Task status"),
            "selected_model_id", Map.of(
                    "cn", "执行此任务使用的模型 ID。见系统提示词「模型选择策略」。若任务结果不满意，可通过 todo_modify 更换更强的模型 ID 后重试。",
                    "en", "Model ID for this task. See 'Model Selection Strategy' in system prompt. If task result is unsatisfactory, update via todo_modify and retry."
            )
    );

    private static final Map<String, Map<String, String>> TODO_MODIFY_PARAMS = Map.of(
            "action", Map.of("cn", "要执行的操作类型", "en", "Operation type to perform"),
            "ids", Map.of("cn", "要操作的任务 ID 列表", "en", "List of task IDs to operate on"),
            "todo_data", Map.of("cn", "用于 insert_after/insert_before 操作的对象", "en", "Object for insert_after/insert_before actions"),
            "todo_data_target_id", Map.of("cn", "目标任务 ID", "en", "Target task ID"),
            "todo_data_items", Map.of("cn", "要插入的任务列表", "en", "Tasks to insert"),
            "todos", Map.of(
                    "cn", "根据 action 字段处理的待办事项数组。支持修改 selected_model_id：若某任务执行结果质量不佳（输出不准确、逻辑错误、未达预期），应将 selected_model_id 更新为更高等级的模型 ID，然后将任务状态重置为 pending 或 in_progress 以触发重新执行。",
                    "en", "Array of todo items to process based on the action field. Supports updating selected_model_id: if a task produces poor results (inaccurate output, logical errors, unmet objectives), update selected_model_id to a model ID whose description indicates stronger capability, and reset the task status to pending or in_progress to trigger re-execution."
            )
    );

    private static final Map<String, Map<String, String>> TODO_GET_PARAMS = Map.of(
            "id", Map.of("cn", "任务唯一标识符", "en", "Unique task identifier")
    );

    private TodoPromptToolProviders() {
    }

    public static Map<String, Object> getTodoCreateInputParams(String language) {
        Map<String, Object> itemProperties = todoItemProperties(language);

        Map<String, Object> taskItems = new LinkedHashMap<>();
        taskItems.put("type", "object");
        Map<String, Object> taskItemProperties = new LinkedHashMap<>();
        taskItemProperties.put("id", itemProperties.get("id"));
        taskItemProperties.put("content", itemProperties.get("content"));
        taskItemProperties.put("activeForm", itemProperties.get("activeForm"));
        taskItemProperties.put("description", itemProperties.get("description"));
        taskItemProperties.put("selected_model_id", itemProperties.get("selected_model_id"));
        taskItems.put("properties", taskItemProperties);
        taskItems.put("required", List.of("id", "content", "activeForm", "description"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("tasks", Map.of(
                "type", "array",
                "description", localized(TODO_CREATE_PARAMS, "tasks", language),
                "items", taskItems
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("tasks"));
        return schema;
    }

    public static Map<String, Object> getTodoListInputParams(String language) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        schema.put("required", List.of());
        return schema;
    }

    public static Map<String, Object> getTodoModifyInputParams(String language) {
        Map<String, Object> itemProperties = todoItemProperties(language);
        Map<String, Object> todoItemSchema = new LinkedHashMap<>();
        todoItemSchema.put("type", "object");
        todoItemSchema.put("properties", itemProperties);
        todoItemSchema.put("required", List.of("id"));

        Map<String, Object> todoData = new LinkedHashMap<>();
        todoData.put("type", "object");
        todoData.put("description", localized(TODO_MODIFY_PARAMS, "todo_data", language));
        todoData.put("properties", Map.of(
                "target_id", Map.of(
                        "type", "string",
                        "description", localized(TODO_MODIFY_PARAMS, "todo_data_target_id", language)
                ),
                "items", Map.of(
                        "type", "array",
                        "description", localized(TODO_MODIFY_PARAMS, "todo_data_items", language),
                        "items", todoItemSchema
                )
        ));
        todoData.put("required", List.of("target_id", "items"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of(
                "type", "string",
                "description", localized(TODO_MODIFY_PARAMS, "action", language),
                "enum", List.of("update", "delete", "cancel", "append", "insert_after", "insert_before")
        ));
        properties.put("ids", Map.of(
                "type", "array",
                "description", localized(TODO_MODIFY_PARAMS, "ids", language),
                "items", Map.of("type", "string")
        ));
        properties.put("todos", Map.of(
                "type", "array",
                "description", localized(TODO_MODIFY_PARAMS, "todos", language),
                "items", todoItemSchema
        ));
        properties.put("todo_data", todoData);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("action"));
        return schema;
    }

    public static Map<String, Object> getTodoGetInputParams(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of(
                "type", "string",
                "description", localized(TODO_GET_PARAMS, "id", language)
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("id"));
        return schema;
    }

    private static Map<String, Object> todoItemProperties(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", property("string", localized(TODO_ITEM_PARAMS, "id", language)));
        properties.put("content", property("string", localized(TODO_ITEM_PARAMS, "content", language)));
        properties.put("activeForm", property("string", localized(TODO_ITEM_PARAMS, "activeForm", language)));
        properties.put("description", property("string", localized(TODO_ITEM_PARAMS, "description", language)));
        Map<String, Object> status = property("string", localized(TODO_ITEM_PARAMS, "status", language));
        status.put("enum", List.of("pending", "in_progress", "completed", "cancelled"));
        properties.put("status", status);
        properties.put("selected_model_id", property("string", localized(TODO_ITEM_PARAMS, "selected_model_id", language)));
        return properties;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static String localized(Map<String, Map<String, String>> values, String key, String language) {
        Map<String, String> descriptions = values.get(key);
        return descriptions.getOrDefault(language, descriptions.get("cn"));
    }

    /**
     * Mirrors Python's {@code TodoCreateMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/todo.py}.
     */
    public static final class TodoCreateMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "todo_create";
        }

        @Override
        public String getDescription(String language) {
            return TODO_CREATE_DESCRIPTION.getOrDefault(language, TODO_CREATE_DESCRIPTION.get("cn"));
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getTodoCreateInputParams(language);
        }
    }

    /**
     * Mirrors Python's {@code TodoListMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/todo.py}.
     */
    public static final class TodoListMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "todo_list";
        }

        @Override
        public String getDescription(String language) {
            return TODO_LIST_DESCRIPTION.getOrDefault(language, TODO_LIST_DESCRIPTION.get("cn"));
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getTodoListInputParams(language);
        }
    }

    /**
     * Mirrors Python's {@code TodoModifyMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/todo.py}.
     */
    public static final class TodoModifyMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "todo_modify";
        }

        @Override
        public String getDescription(String language) {
            return TODO_MODIFY_DESCRIPTION.getOrDefault(language, TODO_MODIFY_DESCRIPTION.get("cn"));
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getTodoModifyInputParams(language);
        }
    }

    /**
     * Mirrors Python's {@code TodoGetMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/todo.py}.
     */
    public static final class TodoGetMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "todo_get";
        }

        @Override
        public String getDescription(String language) {
            return TODO_GET_DESCRIPTION.getOrDefault(language, TODO_GET_DESCRIPTION.get("cn"));
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getTodoGetInputParams(language);
        }
    }
}
