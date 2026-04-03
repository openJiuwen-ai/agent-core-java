# com.openjiuwen.core.graph.pregel.BarrierMessage

## 类 BarrierMessage

```java
public class BarrierMessage extends Message
```

用于 N→1 汇聚同步的 barrier 消息。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BarrierMessage(String sender, String target)` | 创建不带载荷的 `BarrierMessage`。 |
| `public BarrierMessage(String sender, String target, Object payload)` | 创建带载荷的 `BarrierMessage`。 |

## 相关测试

- `ChannelTest`
- `PregelTest`
