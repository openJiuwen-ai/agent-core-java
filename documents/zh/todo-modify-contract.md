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

`status`、`result_summary`、`task_id` 不属于顶层参数。`update` 必须提供非空 `todos`，
每项包含非空 `id` 和至少一个修改字段；仅传 ID 的调用会失败，避免将无修改误报为成功。
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

回归测试入口：`core/src/test/java/com/openjiuwen/harness/tools/TodoModifyFunctionTest.java`。

## 附件分析中发现的 writeFile 校验问题

0.1.16 的系统操作工具曾将 Java `Object` 参数统一标为 JSON `object`。
`writeFile` 的 `content` 声明为 `Object`，实际支持字符串，因此正常文本会在工具校验阶段被拒绝。
修正后，Java `Object` 参数不附加错误的 JSON 类型限制；`Map` 参数仍要求 JSON object。
文本、旧结构化文本和直接 Java 调用的二进制内容均通过工具入口测试，通用校验器未修改。

0.1.17 的文件写入接口已改为字符串和字节数组重载，生成的 `content` 为 string 类型；
该版本的本地 JAR 不会因文本 `content` 触发上述 object 类型校验错误。
