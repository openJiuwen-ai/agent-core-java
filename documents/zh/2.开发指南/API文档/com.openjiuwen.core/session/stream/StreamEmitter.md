# com.openjiuwen.core.session.stream.StreamEmitter

## 类 StreamEmitter

```java
public class StreamEmitter
```

负责把流数据推送到 `AsyncStreamQueue` 的发射器。

## 字段

| 签名 | 说明 |
| --- | --- |
| `public static final String END_FRAME = "all streaming outputs finish"` | 表示流结束的哨兵值。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StreamEmitter()` | 创建一个内部自带默认 `AsyncStreamQueue` 的发射器。 |
| `public StreamEmitter(AsyncStreamQueue streamQueue)` | 使用给定队列创建发射器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public AsyncStreamQueue getStreamQueue()` | 返回底层流队列。 |
| `public void emit(Object streamData)` | 向流队列发送一条流数据；已关闭时抛出异常。 |
| `public boolean isClosed()` | 返回发射器是否已关闭。 |
| `public void close()` | 关闭发射器，并在队列仍可用时发送 `END_FRAME`。 |

## 说明

- 相关测试：`StreamOutputFullTest`、`StreamOutputTest`、`WorkflowInteractionTest`。
