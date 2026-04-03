# com.openjiuwen.core.graph.stream_actor.StreamConsumer

## 接口 StreamConsumer

```java
public interface StreamConsumer
```

由图节点实现的流式消费接口，定义启动、可处理判断与完成状态查询。

## 方法

| 签名 | 说明 |
| --- | --- |
| `void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback)` | 启动当前节点的流式消费逻辑；实现方需要在准备完成后释放 `latch`，并通过 `errorCallback` 上报异常。 |
| `boolean shouldHandleMessage()` | 返回当前节点是否应接收流式消息，通常由组件能力状态决定。 |
| `boolean isDone()` | 返回当前节点是否已完成一次执行周期。 |
