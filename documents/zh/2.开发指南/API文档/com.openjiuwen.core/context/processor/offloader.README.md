# offloader

`com.openjiuwen.core.context.processor.offloader` 提供将超长消息替换为带卸载句柄消息的处理器，包括简单裁剪版和模型总结版。

## Types

| 类型 | 说明 |
|---|---|
| [`MessageOffloader`](./offloader/MessageOffloader.md) | 通过截断内容并保留卸载句柄来替换超长消息。 |
| [`MessageOffloaderConfig`](./offloader/MessageOffloaderConfig.md) | `MessageOffloader` 的阈值、保留消息数和可卸载角色配置。 |
| [`MessageSummaryOffloader`](./offloader/MessageSummaryOffloader.md) | 先调用模型总结消息，再用卸载消息替换原文。 |
| [`MessageSummaryOffloaderConfig`](./offloader/MessageSummaryOffloaderConfig.md) | `MessageSummaryOffloader` 的模型、阈值和自定义提示配置。 |

## Notes

- 两个卸载器都会产出实现了 `OffloadMixin` 的消息类型，后续可通过 `reload_original_context_messages` 工具取回原文。
- `MessageSummaryOffloader` 在模型摘要失败时会回退到 `MessageOffloader` 的截断逻辑。
