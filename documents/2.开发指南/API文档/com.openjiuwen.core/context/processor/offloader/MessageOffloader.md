# com.openjiuwen.core.context.processor.offloader.MessageOffloader

## class MessageOffloader

```java
public class MessageOffloader extends ContextProcessor
```

`MessageOffloader` 会在消息条数或 token 超出阈值时，把符合条件的超长消息替换为裁剪版卸载消息，并把原始内容写入上下文的卸载缓冲区。

## 构造方法

### `public MessageOffloader(MessageOffloaderConfig config)`

读取阈值配置并立即执行额外校验。

**说明**

- 除了 `MessageOffloaderConfig.validate()` 的正数校验外，构造流程还会检查 `trimSize < largeMessageThreshold`，以及 `messagesToKeep < messagesThreshold`。

## 主要方法

### `public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)`

当消息总数超过 `messagesThreshold` 或累计 token 超过 `tokensThreshold` 时返回 `true`。

**说明**

- 如果配置了 `messagesToKeep` 且总消息数尚未超过该保留值，则不会触发。

### `public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd)`

扫描可卸载范围内的消息，把满足角色过滤且内容长度超过 `largeMessageThreshold` 的消息替换成卸载消息。

**说明**

- `keepLastRound == true` 时会尽量保留最近一轮完整对话。
- 已经实现 `OffloadMixin` 的消息不会重复卸载。
- 处理后会把原上下文段和新增消息段重新拆回 `context` 与 `messagesToAdd`。

### `public void loadState(Map<String, Object> state)`

无状态实现，方法体为空。

### `public Map<String, Object> saveState()`

返回空 `HashMap`。

## 受保护扩展点

### `protected BaseMessage offloadMessage(BaseMessage message, ModelContext context)`

默认把消息内容裁剪到 `trimSize` 个字符后追加 `...`，再调用基类的卸载辅助方法创建替代消息。

### `protected static Map<String, Object> extractExtraFields(BaseMessage message)`

提取 `name`、`tool_call_id`、`tool_calls`、`usage_metadata`、`finish_reason`、`parser_content`、`reasoning_content` 等附加字段，供卸载消息保留。

## 说明

- `MessageOffloaderTest` 覆盖了角色过滤、最近消息保留、最近一轮保留、裁剪内容、`tool_call_id` 保留和端到端卸载行为。
- `MessageSummaryOffloader` 继承该类并复用相同的阈值触发与字段保留逻辑。
