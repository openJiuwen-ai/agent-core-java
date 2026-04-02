# com.openjiuwen.core.application.workflow.WorkflowEventHandler

## class WorkflowEventHandler

```java
public class WorkflowEventHandler extends EventHandler
```

`WorkflowEventHandler` 负责工作流 Agent 的意图检测、工作流启动与恢复、中断状态保存以及最终流输出整理。

## 构造方法

### `public WorkflowEventHandler(WorkflowAgentConfig agentConfig, ContextEngine contextEngine)`

基于工作流 Agent 配置与上下文引擎创建事件处理器。

## 公共方法

| 方法 | 说明 |
|---|---|
| `handleInput(EventHandlerInput inputs)` | 统一入口；执行意图检测后决定返回默认响应、恢复任务或启动新任务。 |
| `handleTaskInteraction(EventHandlerInput inputs)` | 目前仅记录日志并返回 `null`。 |
| `handleTaskCompletion(EventHandlerInput inputs)` | 目前仅记录日志并返回 `null`。 |
| `handleTaskFailed(EventHandlerInput inputs)` | 目前仅记录日志并返回 `null`。 |

## 行为摘要

- 当配置中只有一个工作流时，直接选择该工作流；多个工作流时，会用模型把用户输入映射到 `WorkflowIntent`。
- 若未匹配到工作流且 `defaultResponse.text` 非空，则返回默认回复。
- 支持从结构化 `InteractiveInput` 或会话中已保存的中断状态恢复工作流执行。
- 执行工作流时会透传 `OutputSchema`、`CustomSchema`、`TraceSchema` 到会话；若遇到交互中断，则把任务信息写入 `session.state["workflow_controller"]`。
- `QuestionerContextRegressionTest` 实际验证了中断后再次输入 `InteractiveInput` 可以恢复执行，并且 `ConstrainConfig` 会影响上下文窗口大小。
