# com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloader

## class MessageSummaryOffloader

```java
public class MessageSummaryOffloader extends MessageOffloader
```

`MessageSummaryOffloader` 是 `MessageOffloader` 的模型增强版本。它优先调用外部模型生成高密度摘要，再把摘要写成卸载消息；如果模型不可用或摘要失败，则回退到父类的简单裁剪逻辑。

## 构造方法

### `public MessageSummaryOffloader(MessageSummaryOffloaderConfig config)`

把总结器配置转换成父类可识别的 `MessageOffloaderConfig`，初始化模型客户端并再次执行配置校验。

## 继承后的行为

### `protected BaseMessage offloadMessage(BaseMessage message, ModelContext context)`

优先调用模型，以系统提示词 + 原消息内容生成摘要，再创建卸载消息。

**说明**

- 自定义提示词为空时使用内置高密度摘要提示。
- 摘要失败时会记录 warning，并回退到 `MessageOffloader.offloadMessage(...)` 的裁剪实现。

### `protected void validateConfig()`

额外校验 `messagesToKeep < messagesThreshold`，避免“保留条数不小于触发阈值”的无效配置。

## 说明

- `MessageSummaryOffloaderTest` 主要覆盖构造期校验、默认值和自定义配置；完整摘要行为仍依赖真实模型调用。
- `processorType()` 仍返回具体实现类名，因此可通过 `ContextEngine.registerProcessor("MessageSummaryOffloader", ...)` 注册使用。
