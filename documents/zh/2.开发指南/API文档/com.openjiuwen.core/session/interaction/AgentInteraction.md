# com.openjiuwen.core.session.interaction.AgentInteraction

## 类 AgentInteraction

```java
public class AgentInteraction extends BaseInteraction
```

agent 级交互处理器，用于在当前 agent 执行链路中发起用户输入等待。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AgentInteraction(BaseSession session)` | 绑定一个 `BaseSession`，并初始化交互输入队列。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object waitUserInputs(Object value)` | 优先消费已有交互输入；若没有，则通知 checkpointer 打断 agent，并输出一条 `InteractionOutput` 后抛出 `AgentInterrupt`。 |

## 说明

- 该方法会把 `value` 封装为 `InteractionOutput(session.sessionId(), value)`，并通过 output writer 发出交互事件。
