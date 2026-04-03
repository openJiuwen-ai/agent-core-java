# com.openjiuwen.core.application.llm.TaskInterruptionState

## class TaskInterruptionState

```java
public class TaskInterruptionState
```

`TaskInterruptionState` 用于封装一次任务中断时的完整现场，供 `LlmEventHandler` 在恢复执行前保存和传递上下文。

## 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `task` | `Task` | 当前被打断的任务。 |
| `session` | `AgentSessionApi` | 所属会话。 |
| `aiMessage` | `AssistantMessage` | 触发当前任务的模型输出。 |
| `remainingTasks` | `List<Task>` | 当前轮次尚未执行完的任务列表。 |
| `interactionData` | `List<Object>` | 与中断相关的交互输出。 |
| `currentIteration` | `Integer` | 当前 ReAct 轮次。 |

## 构造方法

### `public TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage, List<Task> remainingTasks)`

创建仅包含核心中断信息的状态对象。

### `public TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage, List<Task> remainingTasks, List<Object> interactionData, Integer currentIteration)`

创建包含交互数据与当前轮次的完整状态对象。

## 访问器

- `getTask()` / `getSession()` / `getAiMessage()` / `getRemainingTasks()` 返回构造时保存的只读核心字段。
- `getInteractionData()` 与 `setInteractionData(...)` 用于读取或更新交互输出。
- `getCurrentIteration()` 与 `setCurrentIteration(...)` 用于读取或更新当前轮次。
