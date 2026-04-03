# com.openjiuwen.core.context.processor.compressor.DialogueCompressor

## class DialogueCompressor

```java
public class DialogueCompressor extends ContextProcessor
```

`DialogueCompressor` 用于压缩完整的工具调用轮次，即“用户消息 -> 助手发起工具调用 -> 工具结果 -> 最终助手回答”这一整段对话。

## 构造方法

### `public DialogueCompressor(DialogueCompressorConfig config)`

根据配置初始化压缩提示词、消息/ token 阈值、保留消息策略和可选模型客户端。

## 主要方法

### `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)`

当总消息数超过 `messagesThreshold`，或累计 token 超过 `tokensThreshold` 时返回 `true`。

### `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)`

查找可压缩的完整轮次并逐轮压缩，把压缩后的助手消息回写到上下文中。

**说明**

- `getCompressPairs(...)` 只会选择包含用户消息和“无工具调用最终助手消息”的完整轮次。
- `keepLastRound == true` 时，会优先保留最近一轮不参与压缩。
- 压缩后的替代消息通过 `offloadMessages("assistant", ...)` 创建，因此原轮次内容仍可被重载工具找回。

### `public void loadState(Map<String, Object> state)`

无状态实现，方法体为空。

### `public Map<String, Object> saveState()`

返回空映射。

## 说明

- 该处理器同样依赖外部模型返回 `{"summary": ...}` JSON；模型不可用或调用失败时会保留原始消息。
- `DialogueCompressorTest` 覆盖了消息阈值、token 阈值、`messagesToKeep` 抑制触发以及无状态行为。
