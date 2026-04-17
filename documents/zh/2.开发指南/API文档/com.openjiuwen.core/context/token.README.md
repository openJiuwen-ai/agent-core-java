# token

`com.openjiuwen.core.context.token` 提供 token 计数的抽象接口与默认的字符长度近似实现。

## Types

| 类型 | 说明 |
|---|---|
| [`SimpleTokenCounter`](./token/SimpleTokenCounter.md) | 基于字符长度和消息包裹开销的启发式 token 计数器。 |
| [`TokenCounter`](./token/TokenCounter.md) | 文本、消息列表和工具定义 token 统计的统一抽象。 |

## Notes

- `SimpleTokenCounter` 用于 Java 侧缺少原生 `tiktoken` 绑定时的回退实现。
- `SessionModelContext` 与 `ContextWindow` 的 token 统计都依赖 `TokenCounter` 接口。
