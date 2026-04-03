# context

`com.openjiuwen.core.context` 提供上下文引擎、上下文窗口、统计对象以及状态持久化和卸载扩展抽象，覆盖根级类型与 `context`、`schema`、`token` 三个子包。

## Modules

| 模块 | 说明 |
|---|---|
| [`context`](./context/context.README.md) | 会话上下文实现、消息缓冲、重载工具与 KV Cache 管理。 |
| [`processor`](./context/processor.README.md) | 上下文处理器抽象，以及压缩器和卸载器实现。 |
| [`schema`](./context/schema.README.md) | 上下文引擎配置与卸载消息 schema。 |
| [`token`](./context/token.README.md) | Token 计数抽象与默认启发式实现。 |

## Types

| 类型 | 说明 |
|---|---|
| [`ContextEngine`](./context/ContextEngine.md) | 上下文生命周期管理入口，负责上下文缓存、处理器注册与状态持久化。 |
| [`ContextStats`](./context/ContextStats.md) | 上下文或上下文窗口的消息数、轮次数与 token 统计快照。 |
| [`ContextWindow`](./context/ContextWindow.md) | 发往 LLM 的系统消息、上下文消息、工具定义与统计信息聚合对象。 |
| [`ModelContext`](./context/ModelContext.md) | 上下文实现必须满足的抽象 API 契约。 |
| [`OffloadCapableContext`](./context/OffloadCapableContext.md) | 支持卸载消息的上下文扩展接口。 |
| [`StatefulContext`](./context/StatefulContext.md) | 支持保存/恢复上下文状态的接口。 |

## Notes

- 本包文档以 `ContextEngine.java`、`ModelContext.java`、`SessionModelContext.java` 与对应单测为依据。
- 根包类型主要定义上下文引擎入口、统一抽象和统计/窗口对象，具体实现集中在 `context` 与 `processor` 子包。
