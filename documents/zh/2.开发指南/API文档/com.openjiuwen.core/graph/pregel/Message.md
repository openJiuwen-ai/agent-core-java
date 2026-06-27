# com.openjiuwen.core.graph.pregel.Message

## 类 Message

```java
public class Message
```

在 Pregel 节点之间经由 channel 传递的基础消息。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `sender` | `String` | `-` | 发送该消息的节点名。 |
| `target` | `String` | `-` | 接收该消息的目标 channel 键。 |
| `payload` | `Object` | `-` | 随消息携带的附加载荷。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Message(String sender, String target)` | 创建不带载荷的 `Message`。 |
| `public Message(String sender, String target, Object payload)` | 创建带载荷的 `Message`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getSender()` | 返回当前 `sender`。 |
| `public String getTarget()` | 返回当前 `target`。 |
| `public Object getPayload()` | 返回当前 `payload`。 |
| `public String toString()` | 返回仅包含 `sender` 与 `target` 的调试字符串。 |

## 相关测试

- `ChannelTest`
- `GraphStoreTest`
- `PregelTest`
- `TaskExecutorPoolTest`
