# com.openjiuwen.core.runner.drunner.server_adapter.MqServerAdapter

## 类 MqServerAdapter

```java
public class MqServerAdapter
```

`MqServerAdapter` 是基于 MQ 的服务端适配器，负责接收分布式请求并调用同步或流式处理器。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `adapterId` | `String` | `-` | 当前适配器标识。 |
| `topic` | `String` | `-` | 当前服务端监听的消息 topic。 |
| `invokeHandler` | `Function<Map<String, Object>, Object>` | `-` | 处理同步调用的函数。 |
| `streamHandler` | `Function<Map<String, Object>, Iterator<Object>>` | `-` | 处理流式调用的函数。 |
| `executor` | `ExecutorService` | `Executors.newCachedThreadPool()` | 执行请求处理任务的线程池。 |
| `scheduler` | `ScheduledExecutorService` | `Executors.newSingleThreadScheduledExecutor()` | 处理过期取消的调度器。 |
| `runningTasks` | `Map<String, MessageTask>` | `new ConcurrentHashMap<>()` | 当前正在执行的请求任务映射。 |
| `mq` | `MessageQueueBase` | `-` | 当前绑定的消息队列实例。 |
| `subscription` | `SubscriptionBase` | `-` | 当前 topic 对应的底层订阅对象。 |
| `active` | `boolean` | `-` | 服务端适配器是否已激活。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MqServerAdapter(String adapterId, String topic, Function<Map<String, Object>, Object> invokeHandler, Function<Map<String, Object>, Iterator<Object>> streamHandler)` | 使用适配器标识、监听 topic 与同步/流式处理函数创建服务端适配器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void start()` | 启动适配器并订阅目标 topic，开始处理请求消息。 |
| `public void stop()` | 取消订阅、终止所有运行中任务并关闭执行器。 |
