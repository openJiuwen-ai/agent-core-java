# com.openjiuwen.core.controller.schema.Event

## class Event

```java
public class Event
```

`Event` 是控制器事件体系的基类。`InputEvent`、`TaskInteractionEvent`、`TaskCompletionEvent` 和 `TaskFailedEvent` 都在它的基础上扩展各自载荷。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `eventType` | `EventType` | `null` | 事件类型。 |
| `eventId` | `String` | 随机 UUID | 事件唯一标识。 |
| `metadata` | `Map<String, Object>` | 空 `HashMap` | 补充元数据。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `Event()` | 生成随机 `eventId` 并初始化空元数据。 |
| `Event(EventType eventType)` | 在默认初始化基础上额外设置事件类型。 |

## 说明

- `setMetadata()` 在传入 `null` 时会自动回退为空映射，避免上层出现空指针。
- 子类通常在构造函数中固定自己的 `eventType`，例如 `InputEvent` 固定为 `INPUT`。
