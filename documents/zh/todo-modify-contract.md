# todo_modify 参数约定

`todo_modify` 的模型工具定义使用统一的更新格式。`action` 必填，更新字段放在 `todos` 数组的每个任务对象中：

```json
{
  "action": "update",
  "todos": [
    {"id": "task-1", "status": "completed", "result_summary": "已完成附件分析"}
  ]
}
```

`status`、`result_summary`、`task_id` 不属于顶层参数。工具描述沿用 0.1.17 的中英文说明，
包含各 action 的 JSON 示例、单个 `in_progress` 限制、插入目标状态和模型选择规则。
根 Schema 不额外设置 `additionalProperties`；0.1.16 的通用校验器仍拒绝未声明的顶层字段。
保留 0.1.16 的任务字段：`content`、`activeForm`、`description`、`status`、`priority`、
`selected_model_id`、`depends_on`、`result_summary`、`meta_data`。

其他操作沿用原行为：`delete`/`cancel` 使用非空 `ids`；`append` 使用 `todos`；
`insert_after`/`insert_before` 使用 `todo_data.target_id` 和 `todo_data.items`。
新增、插入的任务须提供 `id`、`content`、`activeForm`、`description`、`status`。

历史程序调用的 `updates[].task_id` 在 `TaskPlanningRail` 注册的 `TodoModifyFunction` 中，
于 `LocalFunction` 校验之前转换为 `todos[].id`；旧参数没有 `action` 时补为 `update`。
同时提供 `todos` 和 `updates` 时仍以 `todos` 为准。旧别名不再展示给模型。
`invoke` 和 `stream` 使用相同的转换过程，不修改调用方的原始参数。
直接调用 `TodoTool.modify` 的旧 Java API 保持原样；通用 `SchemaUtils` 的校验行为未修改。

适配层只负责旧参数转换，不增加任务更新的业务校验。空更新、任务不存在等情况仍由
0.1.16 的 `TodoTool` 返回失败的 `ToolOutput`；仅传合法任务 ID 时，沿用原有成功返回且不修改
任务内容的行为。顶层字段错误或缺少 `action` 的规范调用仍在通用 Schema 校验阶段抛出异常。

回归测试入口：`core/src/test/java/com/openjiuwen/harness/tools/TodoModifyFunctionTest.java`。
