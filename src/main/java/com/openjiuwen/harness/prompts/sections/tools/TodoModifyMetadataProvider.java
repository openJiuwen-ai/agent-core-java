/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Metadata provider for todo list modification actions.
 *
 * @since 0.1.12
 */
public final class TodoModifyMetadataProvider implements ToolMetadataProvider {
  @Override
  /** Auto-generated for codecheck compliance. */
  public String getName() {
    return "todo_modify";
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public String getDescription(String language) {
    return ToolSchemaSupport.localized(
        language,
        "更新当前会话的待办事项列表，可修改状态、内容、优先级与顺序。",
        "Update the todo list for the current session, including status, content, priority, and"
            + " order.");
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public Map<String, Object> getInputParams(String language) {
    Map<String, Object> todoItemSchema =
        Map.of(
            "type", "object",
            "properties",
                ToolSchemaSupport.properties(
                    new Object[] {
                      "id",
                          ToolSchemaSupport.property(
                              "string", text(language, "待办事项 ID", "Todo item id")),
                      "content",
                          ToolSchemaSupport.property(
                              "string", text(language, "任务内容", "Task content")),
                      "activeForm",
                          ToolSchemaSupport.property(
                              "string", text(language, "任务进行态", "Present-tense task form")),
                      "description",
                          ToolSchemaSupport.property(
                              "string", text(language, "任务详细描述", "Detailed task description")),
                      "status",
                          ToolSchemaSupport.enumProperty(
                              "string",
                              List.of("pending", "in_progress", "completed", "cancelled"),
                              text(language, "任务状态", "Task status")),
                      "priority",
                          ToolSchemaSupport.enumProperty(
                              "string",
                              List.of("low", "medium", "high"),
                              text(language, "任务优先级", "Task priority")),
                      "selected_model_id",
                          ToolSchemaSupport.property(
                              "string",
                              text(
                                  language,
                                  "执行该任务时优先使用的模型 ID",
                                  "Model id preferred for this task")),
                      "depends_on",
                          Map.of(
                              "type", "array",
                              "items", Map.of("type", "string"),
                              "description", text(language, "依赖的任务 ID 列表", "Dependent task ids")),
                      "result_summary",
                          ToolSchemaSupport.property(
                              "string",
                              text(
                                  language, "任务完成或阶段性结果摘要", "Task completion or progress summary")),
                      "meta_data",
                          Map.of(
                              "type",
                              "object",
                              "description",
                              text(language, "额外任务元数据", "Additional task metadata"))
                    }),
            "required", List.of("id"));
    return ToolSchemaSupport.objectSchema(
        ToolSchemaSupport.properties(
            new Object[] {
              "session_id",
                  ToolSchemaSupport.property(
                      "string",
                      text(language, "会话 ID，默认当前会话", "Session id, defaults to current session")),
              "action",
                  ToolSchemaSupport.enumProperty(
                      "string",
                      List.of(
                          "update", "delete", "cancel", "append", "insert_after", "insert_before"),
                      text(language, "要执行的操作类型", "Operation type to perform")),
              "ids",
                  Map.of(
                      "type", "array",
                      "items", Map.of("type", "string"),
                      "description",
                          text(
                              language,
                              "delete/cancel 操作的任务 ID 列表",
                              "Task ids for delete/cancel actions")),
              "todos",
                  Map.of(
                      "type",
                      "array",
                      "items",
                      todoItemSchema,
                      "description",
                      text(
                          language,
                          "update/append 操作的任务对象列表",
                          "Todo item objects for update/append actions")),
              "todo_data",
                  Map.of(
                      "type", "object",
                      "properties",
                          ToolSchemaSupport.properties(
                              new Object[] {
                                "target_id",
                                    ToolSchemaSupport.property(
                                        "string", text(language, "目标任务 ID", "Target task id")),
                                "items",
                                    Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        todoItemSchema,
                                        "description",
                                        text(language, "要插入的任务列表", "Todo items to insert"))
                              }),
                      "required", List.of("target_id", "items"),
                      "description",
                          text(
                              language,
                              "insert_after/insert_before 操作的数据",
                              "Data for insert_after/insert_before actions")),
              "updates",
                  Map.of(
                      "type", "array",
                      "items",
                          Map.of(
                              "type", "object",
                              "properties",
                                  ToolSchemaSupport.properties(
                                      new Object[] {
                                        "task_id",
                                            ToolSchemaSupport.property(
                                                "string",
                                                text(language, "待办事项 ID", "Todo item id")),
                                        "content",
                                            ToolSchemaSupport.property(
                                                "string",
                                                text(language, "更新后的任务内容", "Updated task content")),
                                        "status",
                                            ToolSchemaSupport.enumProperty(
                                                "string",
                                                List.of(
                                                    "pending",
                                                    "in_progress",
                                                    "completed",
                                                    "cancelled"),
                                                text(language, "任务状态", "Task status")),
                                        "priority",
                                            ToolSchemaSupport.enumProperty(
                                                "string",
                                                List.of("low", "medium", "high"),
                                                text(language, "任务优先级", "Task priority")),
                                        "selected_model_id",
                                            ToolSchemaSupport.property(
                                                "string",
                                                text(
                                                    language,
                                                    "执行该任务时优先使用的模型 ID",
                                                    "Model id preferred for this task")),
                                        "depends_on",
                                            Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description",
                                                    text(
                                                        language,
                                                        "依赖的任务 ID 列表",
                                                        "Dependent task ids")),
                                        "result_summary",
                                            ToolSchemaSupport.property(
                                                "string",
                                                text(
                                                    language,
                                                    "任务完成或阶段性结果摘要",
                                                    "Task completion or progress summary"))
                                      }),
                              "required", List.of("task_id")),
                      "description",
                          text(language, "兼容旧调用的待办事项更新列表", "Legacy todo item updates to apply"))
            }),
        List.of());
  }

  private String text(String language, String cn, String en) {
    return ToolSchemaSupport.localized(language, cn, en);
  }
}
