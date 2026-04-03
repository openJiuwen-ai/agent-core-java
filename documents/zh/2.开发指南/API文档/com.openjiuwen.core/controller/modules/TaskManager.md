# com.openjiuwen.core.controller.modules.TaskManager

## class TaskManager

```java
public class TaskManager
```

`TaskManager` 是控制器任务子系统的核心存储与索引层，负责任务的增删改查、父子关系维护、优先级索引、状态更新和序列化恢复。

## 内部索引

| 成员 | 类型 | 说明 |
|---|---|---|
| `tasks` | `Map<String, Task>` | 任务主存储。 |
| `priorityIndex` | `Map<Integer, List<String>>` | 优先级到任务 ID 列表的索引。 |
| `parentToChildren` | `Map<String, Set<String>>` | 父任务到子任务集合的映射。 |
| `childToParent` | `Map<String, String>` | 子任务到父任务的反向映射。 |
| `rootTasks` | `Set<String>` | 根任务集合。 |
| `lock` | `ReentrantLock` | 保护并发访问。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getState()` | `TaskManagerState` | 导出当前任务管理器状态，内部会对任务和索引做复制。 |
| `loadState(TaskManagerState state)` | `void` | 用已保存状态完全替换当前内存状态。 |
| `clearState()` | `void` | 清空任务和全部索引。 |
| `addTask(Task task)` / `addTask(List<Task> taskList)` | `void` | 新增一个或多个任务，并更新优先级和父子关系索引。 |
| `getTask(TaskFilter taskFilter)` | `List<Task>` | 按过滤条件查询任务，并返回深拷贝结果。 |
| `popTask(TaskFilter taskFilter)` | `List<Task>` | 查询并移除任务；支持 `highest` 优先级。 |
| `updateTask(Task task)` / `updateTask(List<Task> taskList)` | `boolean` | 更新已有任务，必要时同步调整索引和父子关系。 |
| `removeTask(TaskFilter taskFilter)` | `void` | 按过滤条件删除任务。 |
| `getChildTask(String/List<String> taskId, boolean isRecursive)` | `List<Task>` | 查询子任务，可递归展开。 |
| `updateTaskStatus(...)` | `void` | 更新任务状态，可连带更新子任务状态，并在失败时写入错误消息。 |
| `setPriority(...)` | `void` | 更新任务优先级，可连带子任务一起更新。 |

## 说明

- 所有公开读写方法都通过 `ReentrantLock` 保护，单元测试覆盖了并发新增、读取、更新、删除、弹出和优先级调整场景。
- `addTask()` 会拒绝重复 `taskId`；`popTask(byHighestPriority())` 会选取数值最大的优先级。
- 删除父任务时，如果某个子任务不在同一批删除列表中，`removeTaskInternal()` 会把该子任务提升为根任务。
- 对外返回的任务对象均为 `Task.copy()` 结果，因此调用方修改查询结果不会直接污染内部状态，除非再调用 `updateTask()` 提交回去。
