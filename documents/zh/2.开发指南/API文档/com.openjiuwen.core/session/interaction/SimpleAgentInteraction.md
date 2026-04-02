# com.openjiuwen.core.session.interaction.SimpleAgentInteraction

## 类 SimpleAgentInteraction

```java
public class SimpleAgentInteraction
```

通过 checkpointer 打断 agent 执行的简化交互器。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SimpleAgentInteraction(BaseSession agentSession)` | 使用给定的 agent session 创建交互器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void waitUserInputs(String message)` | 调用 checkpointer 打断 agent 执行，并抛出带消息的 `AgentInterrupt`。 |

## 说明

- 如果 session 上没有可用的 checkpointer，方法仍会直接抛出 `AgentInterrupt(message)`。
