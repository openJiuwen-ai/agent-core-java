# com.openjiuwen.core.runner.MessageQueueType

## 枚举 MessageQueueType

```java
public enum MessageQueueType
```

定义分布式消息队列配置里可选的消息队列类型，可通过 `getValue()` 读取对应的内部字符串值。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前消息队列类型对应的内部字符串值。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `PULSAR` | 使用 Pulsar 消息队列实现。 |
| `FAKE` | 使用占位消息队列实现。 |
