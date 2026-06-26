# com.openjiuwen.core.session.stream.StreamWriterManager

## 类 StreamWriterManager

```java
public class StreamWriterManager
```

管理不同流模式对应的 writer，并负责流输出的阻塞消费。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StreamWriterManager(StreamEmitter streamEmitter, List<StreamMode> modes)` | 使用给定发射器和默认启用模式创建管理器。 |
| `public StreamWriterManager(StreamEmitter streamEmitter)` | 使用默认模式 `OUTPUT/TRACE/CUSTOM` 创建管理器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static StreamWriterManager createManager(StreamEmitter streamEmitter, List<StreamMode> modes)` | 静态工厂方法。 |
| `public static StreamWriterManager createManager(StreamEmitter streamEmitter)` | 默认模式版本的静态工厂方法。 |
| `public StreamEmitter getStreamEmitter()` | 返回底层 `StreamEmitter`。 |
| `public void streamOutput(long firstFrameTimeoutMs, long timeoutMs, boolean needClose, Consumer<Object> consumer)` | 以回调方式同步消费流输出，直到遇到 `END_FRAME`。 |
| `public void streamOutput(Consumer<Object> consumer)` | 使用默认超时参数消费流输出。 |
| `public Iterator<Object> streamIterator()` | 返回阻塞式流输出迭代器。 |
| `public Iterator<Object> streamIterator(long firstFrameTimeoutMs, long timeoutMs, boolean needClose)` | 返回带超时控制的阻塞式流输出迭代器。 |
| `public List<Object> collectStreamOutput()` | 阻塞收集全部流输出到 `List`。 |
| `public void addWriter(StreamMode key, StreamWriter<?> writer)` | 为指定流模式注册 writer。 |
| `public StreamWriter<?> getWriter(StreamMode key)` | 按模式返回 writer。 |
| `public StreamWriter<OutputSchema> getOutputWriter()` | 返回 output writer。 |
| `public StreamWriter<TraceSchema> getTraceWriter()` | 返回 trace writer。 |
| `public StreamWriter<CustomSchema> getCustomWriter()` | 返回 custom writer。 |
| `public List<StreamMode> getEnabledModes()` | 按枚举声明顺序返回当前启用的流模式。 |
| `public StreamWriter<?> removeWriter(StreamMode key)` | 删除指定模式的 writer；默认 writer 不允许删除。 |

## 说明

- 相关测试：`StreamOutputFullTest`、`StreamOutputTest`、`WorkflowInteractionTest`。
- 构造阶段会自动为默认模式装配 `OutputSchema`、`TraceSchema` 和 `CustomSchema` 三类 writer。
