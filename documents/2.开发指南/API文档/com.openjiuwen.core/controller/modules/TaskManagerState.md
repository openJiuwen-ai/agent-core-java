# com.openjiuwen.core.controller.modules.TaskManagerState

## class TaskManagerState

```java
public class TaskManagerState
```

`TaskManagerState` 是 `TaskManager` 的可序列化快照对象，用于把任务数据、索引和层级关系保存到会话状态后再恢复。

## 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `tasks` | `Map<String, Task>` | 任务快照。 |
| `priorityIndex` | `Map<Integer, List<String>>` | 优先级索引。 |
| `parentToChildren` | `Map<String, Set<String>>` | 父任务到子任务的映射。 |
| `childrenToParent` | `Map<String, String>` | 子任务到父任务的映射。 |
| `rootTasks` | `Set<String>` | 根任务集合。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `toMap()` | `Map<String, Object>` | 把当前状态转换成可持久化的普通 `Map` 结构。 |
| `fromMap(Map<String, Object> map)` | `TaskManagerState` | 从普通 `Map` 恢复 `TaskManagerState`；内部会递归调用 `Task.fromMap()`。 |

## 说明

- `toMap()` 会把 `tasks` 进一步序列化成 `taskId -> task.toMap()` 的结构，便于保存到会话状态。
- `fromMap()` 会把 `priority_index` 的键从字符串重新转回整数，并把 `root_tasks` 恢复为 `Set`。
