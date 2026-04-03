# com.openjiuwen.core.controller.legacy.task.Task

## class Task

```java
public class Task
```

`Task` 是旧版控制器的兼容任务模型，使用 Lombok builder 管理任务标识、状态、输入输出和依赖关系。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `agentId` | `String` | `null` | 所属 agent。 |
| `taskId` | `String` | 空字符串 | 任务 ID。 |
| `taskType` | `TaskType` | `UNDEFINED` | 任务类型。 |
| `description` | `String` | `null` | 任务描述。 |
| `status` | `TaskStatus` | `PENDING` | 任务状态。 |
| `metadata` | `Map<String, Object>` | 空映射 | 附加元数据。 |
| `input` | `TaskInput` | 新实例 | 任务输入。 |
| `result` | `TaskResult` | `null` | 任务结果。 |
| `dependencies` | `List<TaskDependency>` | 空列表 | 依赖列表。 |
| `dependents` | `Set<String>` | 空集合 | 被哪些任务依赖。 |
| `parentTaskId` | `String` | `null` | 父任务 ID。 |
| `childTaskIds` | `Set<String>` | 空集合 | 子任务 ID 集合。 |
| `groupId` | `String` | `null` | 所属 group。 |
| `level` | `int` | `0` | 层级深度。 |

## 嵌套类型

| 类型 | 说明 |
|---|---|
| `TaskStatus` | 任务状态枚举，覆盖待执行、运行中、成功、失败、已取消和已中断。 |
| `DependencyType` | 依赖关系枚举，覆盖顺序、并行、条件和数据依赖。 |
| `TaskDependency` | 描述依赖任务、依赖类型、触发条件、数据映射以及是否必需。 |
| `TaskInput` | 描述目标标识、目标名称和任务参数。 |
| `TaskResult` | 描述任务结果状态、输出、错误信息与元数据。 |

## 说明

- 该类型依赖 Lombok 生成访问器和 builder。
- `IntentDetectionController` 和旧版 reasoner/planner 主要围绕这一任务模型协作。
