# config

`com.openjiuwen.core.singleagent.legacy.config` 定义旧版单智能体的配置对象，包括通用 agent 元数据、ReAct 约束、工作流控制字段以及默认回复设置。

## 类型

| 类型 | 说明 |
|---|---|
| [`AgentConfig`](./config/AgentConfig.md) | 旧版 agent 的通用元数据与资源引用配置。 |
| [`ConstrainConfig`](./config/ConstrainConfig.md) | 约束上下文轮次与最大迭代次数的校验配置。 |
| [`DefaultResponse`](./config/DefaultResponse.md) | 工作流型 agent 的默认回复模板。 |
| [`IntentDetectionConfig`](./config/IntentDetectionConfig.md) | 意图识别分类阶段的模板与类别配置。 |
| [`LLMCallConfig`](./config/LLMCallConfig.md) | 直接调用模型时使用的请求与客户端配置。 |
| [`LegacyReActAgentConfig`](./config/LegacyReActAgentConfig.md) | 旧版 ReAct agent 的完整运行配置。 |
| [`MemoryConfig`](./config/MemoryConfig.md) | 旧版记忆功能的开关、作用域和附加参数。 |
| [`ReActAgentConfig`](./config/ReActAgentConfig.md) | `LegacyReActAgentConfig` 的弃用别名。 |
| [`WorkflowAgentConfig`](./config/WorkflowAgentConfig.md) | 基于工作流控制器的旧版 agent 配置。 |

## 说明

- `ConstrainConfigValidationTest` 验证了 `ConstrainConfig` 的默认值和正整数校验行为。
- `LegacyReActAgentConfig` 与 `WorkflowAgentConfig` 都继承自 `AgentConfig`，并在其上补充控制器专属字段。
