# singleagent

`com.openjiuwen.core.singleagent` 提供单智能体运行时的基础抽象、能力管理、回调编排，以及面向 `Controller` 与 ReAct 的入口类型。

## 模块

| 模块 | 说明 |
|---|---|
| [`agents`](./singleagent/agents.README.md) | 提供主要的 ReAct 实现、配置对象和可演进变体。 |
| [`legacy`](./singleagent/legacy.README.md) | `legacy` 兼容层文档入口。 |
| [`rail`](./singleagent/rail.README.md) | 定义 rail 生命周期事件、上下文载荷与重试辅助类型。 |
| [`schema`](./singleagent/schema.README.md) | 提供单智能体运行时使用的卡片、结果与制品模型。 |
| [`skills`](./singleagent/skills.README.md) | `skills` 子包文档入口。 |

## 类型

| 类型 | 说明 |
|---|---|
| [`AbilityExecutionError`](./singleagent/AbilityExecutionError.md) | 统一封装能力执行失败的异常类型。 |
| [`AbilityManager`](./singleagent/AbilityManager.md) | 管理工具、工作流、agent 与 MCP 服务能力卡片。 |
| [`AgentCallbackManager`](./singleagent/AgentCallbackManager.md) | 管理回调与 rail 的注册、注销和触发。 |
| [`BaseAgent`](./singleagent/BaseAgent.md) | 单智能体实现的抽象基类。 |
| [`ControllerAgent`](./singleagent/ControllerAgent.md) | 基于 `Controller` 的单智能体实现。 |
| [`ReActAgent`](./singleagent/ReActAgent.md) | 对 `agents.ReActAgent` 的顶层便捷别名。 |
| [`ReActAgentEvolve`](./singleagent/ReActAgentEvolve.md) | 对 `agents.ReActAgentEvolve` 的顶层便捷别名。 |

## 说明

- 相关测试：`AbilityExecutionErrorTest`、`AbilityManagerSupplementTest`、`DataClassCoverageTest`、`AbilityManagerTest`、`AgentCallbackManagerTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`ControllerAgentTest`、`ReActAgentConfigTest`。
