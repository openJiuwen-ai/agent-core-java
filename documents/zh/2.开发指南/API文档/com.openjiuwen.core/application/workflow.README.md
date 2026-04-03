# workflow

`com.openjiuwen.core.application.workflow` 提供基于工作流的应用层 Agent、控制器、事件处理器与意图识别结果对象。

## Types

| 类型 | 说明 |
|---|---|
| [`WorkflowAgent`](./workflow/WorkflowAgent.md) | 基于 `ControllerAgent` 的工作流 Agent。 |
| [`WorkflowController`](./workflow/WorkflowController.md) | 用于绑定 `WorkflowEventHandler` 并暴露意图识别、任务执行与中断处理接口的辅助类。 |
| [`WorkflowEventHandler`](./workflow/WorkflowEventHandler.md) | 执行意图检测、工作流启动或恢复，以及中断状态持久化。 |
| [`WorkflowIntent`](./workflow/WorkflowIntent.md) | 表示工作流意图识别结果的 record。 |

## Notes

- 本包文档以 `WorkflowAgent.java`、`WorkflowController.java`、`WorkflowEventHandler.java`、`WorkflowIntent.java` 与应用层回归测试为依据。
- 页面重点说明公开调用入口、意图判定结果与任务恢复相关状态。
