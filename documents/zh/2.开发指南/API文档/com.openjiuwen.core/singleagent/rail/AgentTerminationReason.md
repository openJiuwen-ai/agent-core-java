# com.openjiuwen.core.singleagent.rail.AgentTerminationReason

## 枚举 AgentTerminationReason

```java
public enum AgentTerminationReason
```

表示一次 ReActAgent 调用的最终终止原因。非终态（例如执行工具后继续下一轮）不属于该枚举。

## 枚举值

| 枚举值 | 说明 |
|---|---|
| `TEXT_TERMINATION` | 模型返回无工具调用的最终文本。 |
| `MAX_ITERATIONS` | ReAct 循环耗尽配置的最大轮次。 |
| `FORCE_FINISH` | Rail 请求立即结束调用。 |
| `TOOL_INTERRUPT` | 工具执行暂停，等待外部交互输入。 |
| `MODEL_ERROR` | 模型调用失败或没有产生可用的终态响应。 |
| `ERROR` | 模型调用之外的执行阶段发生错误。 |

## 说明

- `AgentCallbackContext.getTerminationReason()` 在调用执行期间通常返回 `null`，在 `AFTER_INVOKE` 中可读取最终值。
- 消费方不应根据枚举顺序实现业务逻辑。
