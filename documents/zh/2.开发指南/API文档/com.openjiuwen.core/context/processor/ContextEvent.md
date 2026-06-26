# com.openjiuwen.core.common.logging.events.ContextEvent

## class ContextEvent

```java
public class ContextEvent
```

`ContextEvent` 用于描述某次处理器执行修改了哪些消息，以及修改事件的类型标签。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `eventType` | `String` | `null` | 事件类型，通常取处理器的简单类名。 |
| `messagesToModify` | `List<Integer>` | `[]` | 被处理器替换或修改的消息索引列表。 |

## 说明

- 该类使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`。
- `ContextProcessor.ProcessResult` 会把 `ContextEvent` 与处理后的消息或窗口一起返回给调用方。
