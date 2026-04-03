# com.openjiuwen.core.controller.schema.ControllerOutputPayload

## class ControllerOutputPayload

```java
public class ControllerOutputPayload
```

`ControllerOutputPayload` 是 `ControllerOutputChunk` 的业务载荷，描述当前输出块的类型、数据帧列表和可选元数据。

## 常量与字段

| 名称 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `TASK_PROCESSING` | `String` | `"processing"` | 表示任务仍在处理中。 |
| `ALL_TASKS_PROCESSED` | `String` | `"all_tasks_processed"` | 表示当前会话下已无活跃任务。 |
| `type` | `String` | `null` | 输出类型，可取事件类型值或上面的特殊常量。 |
| `data` | `List<DataFrame>` | 空列表 | 输出载荷。 |
| `metadata` | `Map<String, Object>` | `null` | 补充元数据。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `ControllerOutputPayload()` | 创建空 payload，并初始化空数据列表。 |
| `ControllerOutputPayload(String type, List<DataFrame> data)` | 用字符串类型和数据列表构造 payload。 |
| `ControllerOutputPayload(String type, List<DataFrame> data, Map<String, Object> metadata)` | 额外附带元数据。 |
| `ControllerOutputPayload(EventType eventType, List<DataFrame> data)` | 使用 `EventType.getValue()` 作为类型。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `setData(List<DataFrame> data)` | `void` | 当传入 `null` 时自动回退为空列表。 |
| `allTasksProcessed(String message)` | `ControllerOutputPayload` | 生成 `ALL_TASKS_PROCESSED` payload，并把说明消息包装成 `TextDataFrame`。 |

## 说明

- `TaskScheduler` 主要依据 `type` 判断任务结果是完成、交互、失败还是处理中。
- `Controller.emitCompletionSignalIfIdle()` 与 `TaskScheduler.ensureSessionCompletionSignal()` 都会使用 `allTasksProcessed()` 生成结束信号。
