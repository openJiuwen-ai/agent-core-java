# com.openjiuwen.core.singleagent.rail.InvokeInputs

## 类 InvokeInputs

```java
public class InvokeInputs implements EventInputs
```

用于 `BEFORE_INVOKE` / `AFTER_INVOKE` 事件的输入载荷。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `query` | `String` | `-` | 当前用户查询文本。 |
| `conversationId` | `String` | `-` | 传入的会话 ID。 |
| `result` | `Map<String, Object>` | `-` | 调用完成后写回的结果对象。 |

## 说明

- 相关测试：`ReActAgentEvolveTest`、`ReActAgentTest`、`DataClassCoverageTest`、`AgentCallbackContextTest`、`RailDataClassesTest`。
- 进入 `BEFORE_INVOKE` 时通常只填充 `query` 与 `conversationId`；在 `AFTER_INVOKE` 阶段会补充 `result`。
