# legacy

`com.openjiuwen.core.single_agent.legacy` 提供单智能体旧版兼容层，覆盖旧配置模型、控制器包装、会话包装以及工作流提供器等保留接口。

## 模块

| 模块 | 说明 |
|---|---|
| [`config`](./legacy/config.README.md) | 定义旧版单智能体的配置对象、约束参数和默认回复。 |
| [`schema`](./legacy/schema.README.md) | 定义旧版插件与工作流描述对象。 |

## 类型

| 类型 | 说明 |
|---|---|
| [`AgentSession`](./legacy/AgentSession.md) | 为兼容接口创建并预热 `AgentSessionApi` 的会话工厂。 |
| [`BaseAgent`](./legacy/BaseAgent.md) | 旧版单智能体抽象基类，负责配置、上下文、工具和工作流注册。 |
| [`ControllerAgent`](./legacy/ControllerAgent.md) | 以 `BaseController` 为执行入口的旧版智能体包装器。 |
| [`LegacyApi`](./legacy/LegacyApi.md) | 暴露旧版工厂方法和弃用告警的静态兼容入口。 |
| [`LegacyReActAgent`](./legacy/LegacyReActAgent.md) | 将旧版配置转换为现代 `ReActAgent` 的兼容实现。 |
| [`ReActAgent`](./legacy/ReActAgent.md) | `LegacyReActAgent` 的旧名称包装。 |
| [`TaskSession`](./legacy/TaskSession.md) | 面向旧调用代码的 `Session` 包装器。 |
| [`WorkflowFactory`](./legacy/WorkflowFactory.md) | 每次调用都返回新 `Workflow` 实例的提供器。 |

## 说明

- `LegacyApi`、`TaskSession` 与 `config.ReActAgentConfig` 主要服务于历史接口兼容；新代码应优先使用现代 `singleagent` API。
- `LegacyReActAgent` 与 `ReActAgent` 的实际执行都会委托给 `com.openjiuwen.core.single_agent.agents.ReActAgent`。
