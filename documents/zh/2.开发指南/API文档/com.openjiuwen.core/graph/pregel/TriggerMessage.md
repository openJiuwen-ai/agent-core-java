# com.openjiuwen.core.graph.pregel.TriggerMessage

## 类 TriggerMessage

```java
public class TriggerMessage extends Message
```

在下一次 super-step 激活目标节点的触发消息。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TriggerMessage(String sender, String target)` | 创建不带载荷的 `TriggerMessage`。 |
| `public TriggerMessage(String sender, String target, Object payload)` | 创建带载荷的 `TriggerMessage`。 |

## 相关测试

- `ChannelTest`
- `PregelTest`
