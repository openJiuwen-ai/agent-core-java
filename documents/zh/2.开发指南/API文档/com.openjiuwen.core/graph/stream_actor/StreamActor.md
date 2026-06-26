# com.openjiuwen.core.graph.stream_actor.StreamActor

## 类 StreamActor

```java
public class StreamActor
```

管理单个消费节点的流式调用生命周期，并为其 `COLLECT` / `TRANSFORM` 能力维护独立的 `StreamProcessor`。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `processors` | `Map<ComponentAbility, StreamProcessor>` | `new HashMap<>()` | 每个可消费能力对应一个 `StreamProcessor`。 |
| `task` | `Future<?>` | `null` | 当前 `vertex.streamCall(...)` 所在的虚拟线程任务。 |
| `taskCompletion` | `CompletableFuture<Void>` | `null` | 主 stream 调用的完成通知。 |
| `taskError` | `CompletableFuture<Void>` | `null` | 主 stream 调用的异常通知。 |
| `vertex` | `StreamConsumer` | `-` | 实际执行流式消费逻辑的节点对象。 |
| `nodeId` | `String` | `-` | 当前 actor 对应的节点 ID。 |
| `runningTasks` | `List<RunningTask>` | `new ArrayList<>()` | 已启动处理器任务及其完成句柄。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StreamActor(String nodeId, StreamConsumer vertex, List<ComponentAbility> abilities, List<String> sources, long streamGeneratorTimeoutSeconds)` | 为给定节点创建 actor，并为每个 `abilities` 条目建立共享 source 集合驱动的 `StreamProcessor`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public synchronized void send(Object message, ComponentAbility sourceAbility, boolean firstFrame, String producerId)` | 发送一帧消息到 actor；当首次帧满足启动条件时会先启动 `vertex.streamCall(...)` 和全部处理器任务，然后把 `StreamPayload` 广播给每个 `StreamProcessor`。 |
| `public Map<String, Object> generator(ComponentAbility ability, Map<String, Object> schema, Consumer<Object> streamCallback)` | 为指定能力返回与 `schema` 对齐的阻塞迭代器映射；该能力不存在时返回空映射。 |
| `public synchronized void awaitCompletion()` | 等待主 stream 调用和全部处理器任务完成，单个等待窗口为 5000ms。 |
| `public synchronized void shutdown()` | 取消主任务、异常 future 与全部处理器任务，等待清理完成后重置内部状态。 |
