# com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription

## 类 ReplyTopicSubscription

```java
public class ReplyTopicSubscription
```

`ReplyTopicSubscription` 负责监听 `reply topic`，并将收到的响应分发到对应的 `ResponseCollector`。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `mq` | `MessageQueueBase` | `-` | 提供订阅能力的消息队列实例。 |
| `topic` | `String` | `-` | 当前监听的回复 topic。 |
| `collectors` | `Map<CollectorKey, ResponseCollector>` | `new ConcurrentHashMap<>()` | 已注册的响应收集器映射。 |
| `active` | `boolean` | `-` | 当前订阅是否处于激活状态。 |
| `subscription` | `SubscriptionBase` | `-` | 底层消息队列订阅对象。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ReplyTopicSubscription(MessageQueueBase mq, String topic)` | 使用消息队列实例和回复 topic 创建订阅器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void activate()` | 建立底层订阅并开始分发收到的响应消息。 |
| `public void deactivate()` | 关闭订阅、注销 topic，并终止所有已注册的收集器。 |
| `public boolean isActive()` | 返回当前订阅是否处于激活状态。 |
| `public ResponseCollector registerCollector(String messageId, String remoteId, String requestId, Double ttlSeconds)` | 为指定请求创建并注册响应收集器，同时校验并发上限。 |
| `public void unregisterCollector(String messageId, String remoteId, String requestId)` | 按条件移除收集器；三个参数都为 `null` 时会清空全部收集器。 |
| `public String getTopic()` | 返回当前监听的回复 topic。 |

## 嵌套类型

- CollectorKey：由 remoteId、messageId 和可选 requestId 组成的收集器唯一键。
