# com.openjiuwen.core.single_agent.rail.ToolCallInputs

## 类 ToolCallInputs

```java
public class ToolCallInputs implements EventInputs
```

用于 `BEFORE_TOOL_CALL` / `AFTER_TOOL_CALL` 事件的输入载荷。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `toolCall` | `ToolCall` | `-` | 原始工具调用对象。 |
| `toolName` | `String` | `""` | 当前工具名。 |
| `toolArgs` | `Object` | `-` | 当前工具参数。 |
| `toolResult` | `Object` | `-` | 工具执行的原始结果。 |
| `toolMsg` | `ToolMessage` | `-` | 回填到上下文中的工具消息。 |

## 说明

- 相关测试：`DataClassCoverageTest`、`RailDataClassesTest`。
- builder 默认会把 `toolName` 初始化为空字符串。
