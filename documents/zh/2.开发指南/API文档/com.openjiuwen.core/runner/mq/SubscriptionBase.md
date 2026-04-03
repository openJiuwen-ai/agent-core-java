# com.openjiuwen.core.runner.mq.SubscriptionBase

## 类 SubscriptionBase

```java
public abstract class SubscriptionBase
```

`SubscriptionBase` 定义订阅对象的消息处理器绑定与激活状态控制。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void setMessageHandler(AsyncMessageHandler<Object, Object> handler)` | 为当前订阅设置异步消息处理器。 |
| `public void activate()` | - |
| `public void deactivate()` | - |
| `public boolean isActive()` | - |

## 相关测试

- `MessageQueueInMemoryTest`
