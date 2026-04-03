# com.openjiuwen.core.session.stream.AsyncStreamQueue

## 类 AsyncStreamQueue

```java
public class AsyncStreamQueue
```

基于 `BlockingQueue` 的线程安全阻塞流队列。

## 字段

| 签名 | 说明 |
| --- | --- |
| `public static final long DEFAULT_SEND_ATTEMPT_TIMEOUT_MS = 200` | 单次发送尝试的默认超时时间，单位毫秒。 |
| `public static final int DEFAULT_MAX_SEND_RETRIES = 5` | 发送失败后的默认最大重试次数。 |
| `public static final long DEFAULT_RECEIVE_TIMEOUT_MS = -1` | 默认接收超时时间，`-1` 表示无限等待。 |
| `public static final long DEFAULT_CLOSE_TIMEOUT_MS = 5000` | 默认关闭超时时间，单位毫秒。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AsyncStreamQueue(int maxSize)` | 创建指定容量的流队列；`maxSize = 0` 表示无界队列。 |
| `public AsyncStreamQueue()` | 创建一个无界流队列。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public boolean isClosed()` | 返回队列是否已经关闭。 |
| `public void send(Object data, long attemptTimeout, int maxRetries)` | 按给定超时和重试次数把数据写入队列。 |
| `public void send(Object data)` | 使用默认超时和重试参数发送数据。 |
| `public Object receive(long timeoutMs)` | 按给定超时时间从队列读取一条数据。 |
| `public Object receive()` | 使用默认超时策略读取一条数据。 |
| `public void close(long timeoutMs)` | 关闭队列并强制清空剩余元素。 |
| `public void close()` | 使用默认关闭超时关闭队列。 |

## 说明

- 相关测试：`StreamOutputFullTest`。
- 队列关闭后，`send(...)` 与 `receive(...)` 都会抛出 `IllegalStateException`。
