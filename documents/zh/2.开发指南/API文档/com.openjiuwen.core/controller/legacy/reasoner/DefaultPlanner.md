# com.openjiuwen.core.controller.legacy.reasoner.DefaultPlanner

## class DefaultPlanner

```java
public class DefaultPlanner implements Planner
```

`DefaultPlanner` 是旧版默认 Planner，实现非常轻量：它会根据消息或意图创建一个 `TaskType.UNDEFINED`、`PENDING` 状态的默认任务。

## 构造方法

### `public DefaultPlanner(Object config, Object contextEngine, Session session)`

保存 planner 配置、上下文引擎和会话引用。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `plan(IntentDetectionController.Intent intent, Session session)` | `Task` | 为意图创建一个默认 Planner 任务。 |
| `processMessage(Event event)` | `List<Task>` | 为消息创建任务列表，列表中只有一个默认任务。 |

## 说明

- 通过消息创建任务时，描述文本格式为 `Planner task for message: ...`，并把原消息 ID 写入元数据中的 `original_message_id`。
- 通过意图创建任务时，描述固定为 `Planner task from intent`，并在元数据中写入 `task_source = planner`。
