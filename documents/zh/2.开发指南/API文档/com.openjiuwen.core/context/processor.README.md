# processor

`com.openjiuwen.core.context.processor` 定义上下文处理器的统一抽象、处理事件对象，以及按“压缩”和“卸载”分类的处理器实现。

## Modules

| 模块 | 说明 |
|---|---|
| [`compressor`](./processor/compressor.README.md) | 按当前轮、完整对话轮或多轮级别压缩上下文消息。 |
| [`offloader`](./processor/offloader.README.md) | 把超长消息裁剪或总结后替换成带卸载句柄的消息。 |

## Types

| 类型 | 说明 |
|---|---|
| [`ContextEvent`](./processor/ContextEvent.md) | 描述某次处理器执行修改了哪些消息索引。 |
| [`ContextProcessor`](./processor/ContextProcessor.md) | 处理器生命周期、状态持久化和卸载辅助逻辑的抽象基类。 |

## Notes

- `ContextEngine` 会通过注册表按处理器类型名实例化这些处理器。
- 处理器当前只在 `addMessages()` 和 `getContextWindow()` 两个阶段参与 `SessionModelContext` 生命周期。
