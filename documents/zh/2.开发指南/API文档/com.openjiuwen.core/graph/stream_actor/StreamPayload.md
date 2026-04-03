# com.openjiuwen.core.graph.stream_actor.StreamPayload

## 类 StreamPayload

```java
public class StreamPayload
```

在 producer 与 consumer 之间传递的消息载体，记录原始消息和来源能力。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `message` | `Object` | `-` | 原始消息内容，通常是单键 `Map`。 |
| `sourceAbility` | `ComponentAbility` | `-` | 产生该消息的能力类型，如 `STREAM` 或 `TRANSFORM`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StreamPayload(Object message, ComponentAbility sourceAbility)` | 基于消息内容和来源能力创建 payload。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object getMessage()` | 返回原始消息内容。 |
| `public ComponentAbility getSourceAbility()` | 返回消息来源能力。 |

## 相关测试

- `StreamProcessorTest`
