# context

`com.openjiuwen.core.context.context` 提供 `SessionModelContext` 的核心实现及其配套缓冲、轮次分析、卸载缓存和 KV Cache 管理工具。

## Types

| 类型 | 说明 |
|---|---|
| [`ContextMessageBuffer`](./context/ContextMessageBuffer.md) | 维护历史消息和当前上下文消息的尾部缓冲区。 |
| [`ContextUtils`](./context/ContextUtils.md) | 查找对话轮次、替换消息片段与格式化重载结果的静态工具类。 |
| [`KVCacheManager`](./context/KVCacheManager.md) | 比较前后窗口差异并在需要时触发推理侧 KV Cache 释放。 |
| [`OffloadMessageBuffer`](./context/OffloadMessageBuffer.md) | 以 `in_memory` 方式缓存已卸载消息。 |
| [`SessionModelContext`](./context/SessionModelContext.md) | `ModelContext` 的会话级实现，负责处理器调用、窗口裁剪、统计和状态读写。 |

## Notes

- 本包文档主要依据 `SessionModelContext.java`、`ContextMessageBuffer.java`、`ContextUtils.java` 及对应测试。
- `SessionModelContext` 通过 `ContextProcessor` 链在 `addMessages()` 和 `getContextWindow()` 两个阶段扩展行为。
