# com.openjiuwen.core.application.workflow_agent.WorkflowController

## class WorkflowController

```java
public class WorkflowController
```

`WorkflowController` 是围绕 `WorkflowEventHandler` 的控制器封装，负责绑定配置与上下文引擎，并暴露意图识别、任务执行和中断处理接口。

## 构造方法

### `public WorkflowController()`

创建一个未绑定配置的空控制器。

### `public WorkflowController(WorkflowAgentConfig config, ContextEngine contextEngine)`

立即使用给定配置与上下文引擎完成初始化。

## 主要方法

| 方法 | 说明 |
|---|---|
| `setupFromAgent(WorkflowAgent agent)` | 从现有 Agent 复制配置、上下文引擎与能力管理器。 |
| `handleEvent(Event event, AgentSessionApi session)` | 委托给 `WorkflowEventHandler.handleInput(...)`。 |
| `intentDetection(Event event, AgentSessionApi session)` | 执行工作流意图识别，返回 `WorkflowIntent`。 |
| `execTask(Event event, Task task, AgentSessionApi session)` | 解析目标工作流后执行任务；若找不到工作流则抛出 `IllegalArgumentException`。 |
| `interruptTask(Task task, AgentSessionApi session, List<Object> interactionData)` | 委托事件处理器保存中断状态。 |
| `createMessage(Map<String, Object> inputs)` | 将 `content` 归一化为 `query`，并返回 `InputEvent`。 |
| `getAgentConfig()` | 返回当前绑定的配置。 |
| `getContextEngine()` | 返回当前绑定的 `ContextEngine`。 |
| `getEventHandler()` | 返回当前 `WorkflowEventHandler`；未配置时抛出 `IllegalStateException`。 |

## 说明

- `setupFromAgent()` 之后可直接复用 Agent 上下文中的能力管理器与事件处理器配置。
- `createMessage()` 会保留已有输入字段，并在缺少 `query` 时自动补齐空字符串。
- `execTask()` 会优先按 `task.metadata.target_id`，其次按任务描述匹配 `WorkflowSchema`。
