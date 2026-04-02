# com.openjiuwen.core.runner.drunner.DistributedRunner

## 类 DistributedRunner

```java
public final class DistributedRunner
```

`DistributedRunner` 维护分布式运行所需的消息队列和 reply topic 订阅实例，并按需启动或关闭这些组件。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `MQ` | `AtomicReference<MessageQueueBase>` | `new AtomicReference<>()` | 保存当前已启动的消息队列实例。 |
| `REPLY_SUBSCRIPTION` | `AtomicReference<ReplyTopicSubscription>` | `new AtomicReference<>()` | 保存当前 `reply topic` 订阅器实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static synchronized void ensureStarted()` | 按需创建消息队列并激活 `reply topic` 订阅器。 |
| `public static MessageQueueBase messageQueue()` | 返回当前消息队列实例，必要时会先触发启动。 |
| `public static ReplyTopicSubscription replySubscription()` | 返回当前回复订阅器，必要时会先触发启动。 |
| `public static synchronized void shutdown()` | 关闭回复订阅器并停止消息队列。 |
| `public static String replyTopic()` | 根据 `RunnerConfig` 中的 reply topic 模板生成当前实例的回复 topic。 |
| `public static String agentTopic(String agentId, String version)` | 根据 `RunnerConfig` 中的 agent topic 模板生成指定 Agent 的 topic。 |
