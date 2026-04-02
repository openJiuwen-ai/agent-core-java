# dsubscription

`com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription` 管理 `reply topic` 订阅、取消事件和响应收集器。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`CancelEvent`](dsubscription/CancelEvent.md) | 放入收集器队列中，用于唤醒阻塞等待者的取消事件。 |
| [`CancelReason`](dsubscription/CancelReason.md) | 表示 `ResponseCollector` 被唤醒或取消的原因。 |
| [`ReplyTopicSubscription`](dsubscription/ReplyTopicSubscription.md) | 监听 `reply topic` 并将响应分发到对应的收集器。 |
| [`ResponseCollector`](dsubscription/ResponseCollector.md) | 为单个分布式请求收集响应，并处理取消、TTL 过期和队列满场景。 |
