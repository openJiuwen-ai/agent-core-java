# com.openjiuwen.core.session.interaction.AgentInterrupt

## 类 AgentInterrupt

```java
public class AgentInterrupt extends RuntimeException
```

当 agent 执行因为等待用户输入而被打断时抛出的运行时异常。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AgentInterrupt()` | 使用默认异常消息创建中断异常。 |
| `public AgentInterrupt(String message)` | 使用自定义消息创建中断异常。 |

## 说明

- `AgentInteraction` 和 `SimpleAgentInteraction` 都会通过抛出该异常来中断当前执行流程。
