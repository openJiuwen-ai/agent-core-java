# security

`com.openjiuwen.core.security` 汇总 openJiuwen Java 的安全防护 API。当前公开类型集中在 `guardrail` 子包，用于把风险分析后端接入回调框架事件流。

## Modules

| 模块 | 说明 |
|---|---|
| [`guardrail`](./security/guardrail.README.md) | Guardrail 抽象基类、风险分析后端协议、风险结果模型与内置用户输入护栏。 |

## Notes

- 当前源码范围只有 `guardrail` 子包，没有 `com.openjiuwen.core.security` 根包下的独立公开类型。
- 该任务范围未提供专门 Java 测试，因此页面描述仅基于公开源码行为。
