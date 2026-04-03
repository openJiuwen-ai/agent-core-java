# schema

`com.openjiuwen.core.application.schema` 定义应用层 Agent 使用的配置对象、插件与工作流 Schema，以及默认响应与约束模型。

## Types

| 类型 | 说明 |
|---|---|
| [`AgentMemoryConfig`](./schema/AgentMemoryConfig.md) | 应用层使用的 memory 配置类型入口，继承共享 memory 模块实现。 |
| [`ConstrainConfig`](./schema/ConstrainConfig.md) | 应用层 Agent 的会话窗口与最大迭代约束配置。 |
| [`DefaultResponse`](./schema/DefaultResponse.md) | 工作流意图未命中时的默认回复配置。 |
| [`LlmAgentConfig`](./schema/LlmAgentConfig.md) | ReAct Agent 的完整应用层配置对象。 |
| [`PluginSchema`](./schema/PluginSchema.md) | 工具或插件引用的配置描述。 |
| [`ReActAgentConfig`](./schema/ReActAgentConfig.md) | 基于 `LlmAgentConfig` 的派生配置类型。 |
| [`WorkflowAgentConfig`](./schema/WorkflowAgentConfig.md) | 工作流 Agent 的完整应用层配置对象。 |
| [`WorkflowSchema`](./schema/WorkflowSchema.md) | 工作流引用的配置描述。 |

## Notes

- 这批类型大量使用 Lombok 生成 getter、setter 与 builder；文档仅列出源码显式声明的字段与方法。
- 页面重点说明字段含义、序列化字段名与运行时约束。
