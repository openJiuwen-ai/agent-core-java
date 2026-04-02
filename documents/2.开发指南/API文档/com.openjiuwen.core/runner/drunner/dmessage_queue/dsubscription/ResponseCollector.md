# com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ResponseCollector

## 类 ResponseCollector

```java
public class ResponseCollector
```

`ResponseCollector` 为单个分布式请求收集响应，并处理取消、TTL 过期和队列满等情况。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `MAX_QUEUE_SIZE` | `int` | `10_000` | 收集器内部队列的最大缓存条数。 |
| `TTL_SCHEDULER` | `ScheduledExecutorService` | `-` | 负责触发 TTL 过期检查的调度器。 |
| `CANCEL_SENTINEL` | `DmqResponseMessage` | `new DmqResponseMessage()` | 用于唤醒等待者并传递取消/过期状态的哨兵消息。 |
| `messageId` | `String` | `-` | 当前请求的消息标识。 |
| `receiverId` | `String` | `-` | 远端接收方标识。 |
| `requestId` | `String` | `-` | 可选请求关联标识。 |
| `ttlSeconds` | `double` | `-` | 当前收集器的有效 TTL 秒数。 |
| `queue` | `BlockingQueue<DmqResponseMessage>` | `new LinkedBlockingQueue<>(MAX_QUEUE_SIZE)` | 保存待消费响应消息的队列。 |
| `cancelled` | `boolean` | `-` | 是否已被取消。 |
| `expired` | `boolean` | `-` | 是否已因 TTL 到期而失效。 |
| `cancelReason` | `CancelReason` | `-` | 当前取消原因。 |
| `expireTask` | `ScheduledFuture<?>` | `-` | 与 TTL 过期检查对应的计划任务。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ResponseCollector(String messageId, String receiverId, String requestId, Double ttlSeconds)` | 使用消息标识、接收方和 TTL 创建响应收集器，并注册过期任务。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public boolean isCancelled()` | 返回当前收集器是否已被取消。 |
| `public boolean isExpired()` | 返回当前收集器是否已因 TTL 到期。 |
| `public boolean isActive()` | 返回当前收集器是否仍处于可接收响应状态。 |
| `public void putMessage(DmqResponseMessage message)` | 将收到的响应放入队列；若队列已满会自动取消。 |
| `public Object result(Double timeoutSeconds) throws Exception` | 阻塞等待单次调用结果，并在结束后关闭收集器。 |
| `public Iterator<Object> stream(Double timeoutSeconds)` | 返回流式响应迭代器，按块读取远端结果。 |
| `public void checkMessage(DmqResponseMessage message) throws Exception` | 检查消息是否表示取消、超时或远端错误。 |
| `public void close()` | 以默认原因 `RUNNER_STOPPED` 关闭收集器。 |
| `public void close(CancelReason reason)` | 以指定原因关闭收集器；非 `FINISH` 场景会唤醒等待者。 |
