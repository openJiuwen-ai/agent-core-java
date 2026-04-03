# com.openjiuwen.core.context.processor.compressor.RoundLevelCompressor

## class RoundLevelCompressor

```java
public class RoundLevelCompressor extends ContextProcessor
```

`RoundLevelCompressor` 用于把多个连续、压缩级别一致的完整对话轮合并成一个新的用户消息和一个新的助手消息，从而在长会话场景下进一步缩短上下文。

## 构造方法

### `public RoundLevelCompressor(RoundLevelCompressorConfig config)`

读取轮次数阈值、token 阈值、自定义提示词和模型客户端，并立即执行 `config.validate()`。

## 主要方法

### `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)`

只有在同时满足以下条件时才触发：

- 累计 token 超过 `tokensThreshold`。
- 存在至少一个满足 `roundsThreshold` 的连续完整轮次窗口。

### `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)`

查找符合条件的轮次窗口，按逆序压缩并回写到上下文中。

**说明**

- 处理器会先把消息切分成 `DialogueRound`，再筛选压缩级别一致且索引连续的窗口。
- `keepLastRound == true` 时，最后一轮会被排除在候选窗口之外。
- 压缩成功后会把结果写成一条新的用户消息和一条新的助手消息，并在 `OffloadMixin.metadata` 中写入递增后的 `compress_level`。

### `public void loadState(Map<String, Object> state)`

无状态实现，方法体为空。

### `public Map<String, Object> saveState()`

返回空映射。

## 说明

- 该处理器与 `DialogueCompressor` 一样依赖模型返回 JSON 摘要；失败时会记录 warning 并保留原轮次。
- `RoundLevelCompressorTest` 覆盖了 token 阈值触发、低负载不触发和 builder 赋值行为。
