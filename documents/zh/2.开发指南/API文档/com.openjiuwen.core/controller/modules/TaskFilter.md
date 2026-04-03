# com.openjiuwen.core.controller.modules.TaskFilter

## class TaskFilter

```java
public class TaskFilter
```

`TaskFilter` 用于描述任务查询条件，支持按任务 ID、会话、用户、优先级、状态和根任务标记组合过滤。

## 过滤字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | `String` 或 `List<String>` | 指定单个或多个任务 ID。 |
| `sessionId` | `String` | 按会话过滤任务。 |
| `userId` | `String` | 按 `task.metadata.user_id` 过滤任务。 |
| `priority` | `Integer` 或 `"highest"` | 指定数值优先级，或请求最高优先级任务。 |
| `status` | `TaskStatus` | 按任务状态过滤。 |
| `withChildren` | `boolean` | 查询命中任务后，是否把子任务一并带出。 |
| `isRoot` | `boolean` | 仅匹配根任务。 |

## 常用工厂方法

| 方法 | 说明 |
|---|---|
| `byTaskId(String taskId)` | 查询单个任务。 |
| `byTaskIds(List<String> taskIds)` | 查询多个任务。 |
| `bySessionId(String sessionId)` | 查询某个会话下的任务。 |
| `byStatus(TaskStatus status)` | 查询指定状态的任务。 |
| `byRoot()` | 查询根任务。 |
| `byHighestPriority()` | 请求最高优先级任务，主要供 `TaskManager.popTask()` 使用。 |
| `builder()` | 构造组合过滤条件。 |

## 说明

- Builder 的 `build()` 要求至少设置一个主过滤条件，否则会抛出 `AGENT_CONTROLLER_TASK_PARAM_ERROR`。
- `priority = "highest"` 只适合 `TaskManager.popTask()`；`TaskManager.getTask()` 和 `removeTask()` 都会对该值抛错。
- `withChildren = true` 时，`TaskManager` 会把命中任务的子任务递归收集到结果中。
