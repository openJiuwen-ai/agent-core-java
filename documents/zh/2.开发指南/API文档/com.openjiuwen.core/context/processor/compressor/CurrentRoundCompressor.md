# com.openjiuwen.core.context_engine.processor.compressor.CurrentRoundCompressor

## class CurrentRoundCompressor

```java
public class CurrentRoundCompressor extends ContextProcessor
```

`CurrentRoundCompressor` 用于压缩当前对话轮中“最后一个用户消息之后”的上下文内容，以避免当前轮回复、工具结果或长消息把上下文推到消息数或 token 上限之外。

## 构造方法

### `public CurrentRoundCompressor(CurrentRoundCompressorConfig config)`

根据配置初始化压缩提示词、消息/ token 阈值、保留消息数、单条大消息阈值以及可选模型客户端。

## 主要方法

### `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)`

判断是否需要在消息写入前触发压缩。

**说明**

- 当总消息数超过 `messagesThreshold` 时会立即触发。
- 未超过消息阈值时，会使用 `TokenCounter` 比较累计 token 是否超过 `tokensThreshold`。
- 配置了 `messagesToKeep` 且总消息数仍低于该保留值时，直接不触发。

### `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)`

执行压缩逻辑，并把压缩后的结果回写到 `context`。

**说明**

- `singleMultiCompression == true` 时，会把当前轮中可压缩的连续消息段整体压缩成一个替代消息。
- `singleMultiCompression == false` 时，只会对超过 `largeMessageThreshold` 的单条消息逐条压缩。
- 压缩成功后会通过 `ContextProcessor.offloadMessages(...)` 把原消息写入卸载缓冲，并把替代消息写回上下文。
- 摘要模型返回为空或调用失败时，会记录 warning 并保持原消息不变。

### `public void loadState(Map<String, Object> state)`

无状态实现，方法体为空。

### `public Map<String, Object> saveState()`

返回空映射。

## 说明

- 处理器会跳过“最后一条消息本身就是 `UserMessage`”的情况，避免压缩尚未完成的当前轮。
- `CurrentRoundCompressorTest` 覆盖了消息阈值触发、最后一条为用户消息时不压缩、builder 赋值与无状态行为。
