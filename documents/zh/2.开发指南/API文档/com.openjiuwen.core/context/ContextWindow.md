# com.openjiuwen.core.context.ContextWindow

## class ContextWindow

```java
public class ContextWindow
```

`ContextWindow` 是发往模型推理端的最终窗口对象，聚合系统消息、上下文消息、工具定义和对应的 `ContextStats`。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `systemMessages` | `List<BaseMessage>` | `[]` | 应固定放在最终消息列表开头的系统级消息。 |
| `contextMessages` | `List<BaseMessage>` | `[]` | 已按窗口大小、轮次或处理器规则裁剪后的上下文消息。 |
| `tools` | `List<ToolInfo>` | `[]` | 当前轮可供模型调用的工具定义。 |
| `statistic` | `ContextStats` | `new ContextStats()` | 当前窗口的统计快照。 |

## 显式方法

### `public List<BaseMessage> getMessages()`

按 `systemMessages + contextMessages` 的顺序拼接出最终消息列表。

### `public List<ToolInfo> getToolList()`

返回当前窗口持有的工具定义列表。

## 说明

- `ContextWindowTest` 验证了 builder 默认会创建非空空列表，并确认 `getMessages()` 会保留系统消息在前的顺序。
- 虽然类上使用了 Lombok `@Data`，上下文子系统内部统一通过显式的 `getToolList()` 读取工具列表。
