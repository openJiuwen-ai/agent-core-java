# com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage

## 类 DmqMessage

```java
public abstract class DmqMessage extends QueueMessage
```

`DmqMessage` 是分布式 Runner 的消息基类，会把业务负载保存在 `body` 字段中，同时覆写 `QueueMessage` 的 payload 访问逻辑。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `body` | `Object` | `-` | 实际业务负载。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object getPayload()` | 返回当前消息对象自身，便于内存消息队列传递完整消息。 |
| `public void setPayload(Object payload)` | 将传入 payload 写入 `body` 字段。 |
| `public Object getBody()` | 返回业务负载。 |
| `public void setBody(Object body)` | 设置业务负载。 |
