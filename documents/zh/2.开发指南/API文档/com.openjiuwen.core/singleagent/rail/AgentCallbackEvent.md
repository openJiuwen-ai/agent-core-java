# com.openjiuwen.core.single_agent.rail.AgentCallbackEvent

## 枚举 AgentCallbackEvent

```java
public enum AgentCallbackEvent
```

定义单智能体生命周期中的标准回调事件。

## 枚举值

| 枚举值 | 构造参数 | 说明 |
|---|---|---|
| `BEFORE_INVOKE` | `"before_invoke"` | `agent.invoke()` 开始前触发。 |
| `AFTER_INVOKE` | `"after_invoke"` | `agent.invoke()` 结束后触发。 |
| `BEFORE_MODEL_CALL` | `"before_model_call"` | 调用 LLM 前触发。 |
| `AFTER_MODEL_CALL` | `"after_model_call"` | 收到 LLM 响应后触发。 |
| `ON_MODEL_EXCEPTION` | `"on_model_exception"` | LLM 调用抛出异常时触发。 |
| `BEFORE_TOOL_CALL` | `"before_tool_call"` | 执行工具前触发。 |
| `AFTER_TOOL_CALL` | `"after_tool_call"` | 工具执行完成后触发。 |
| `ON_TOOL_EXCEPTION` | `"on_tool_exception"` | 工具执行抛出异常时触发。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public String getValue()` | 返回事件对应的字符串值。 |
| `@Override public String toString()` | 返回与 `getValue()` 相同的字符串表示。 |

## 说明

- 相关测试：`AgentCallbackManagerTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`DataClassCoverageTest`、`AgentCallbackContextTest`、`AgentCallbackEventTest`、`AgentRailTest`、`RailExecutorTest`。
