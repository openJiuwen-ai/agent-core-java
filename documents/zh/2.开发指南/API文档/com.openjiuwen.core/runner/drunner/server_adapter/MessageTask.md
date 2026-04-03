# com.openjiuwen.core.runner.drunner.server_adapter.MessageTask

## 类 MessageTask

```java
public class MessageTask
```

`MessageTask` 将分布式请求消息与其正在执行的任务句柄绑定在一起。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `message` | `DmqRequestMessage` | `-` | 对应的请求消息。 |
| `task` | `Future<?>` | `-` | 对应的异步执行任务。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MessageTask(DmqRequestMessage message, Future<?> task)` | 绑定请求消息和异步任务。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public DmqRequestMessage getMessage()` | 返回绑定的请求消息。 |
| `public Future<?> getTask()` | 返回绑定的异步任务。 |
