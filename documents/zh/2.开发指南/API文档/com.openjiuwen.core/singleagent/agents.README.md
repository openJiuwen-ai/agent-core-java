# agents

`com.openjiuwen.core.singleagent.agents` 包含主要的 ReAct 单智能体实现、其配置对象，以及引入可演进 operator 的变体。

## 类型

| 类型 | 说明 |
|---|---|
| [`ReActAgent`](./agents/ReActAgent.md) | 基于 ReAct 循环的单智能体实现。 |
| [`ReActAgentConfig`](./agents/ReActAgentConfig.md) | `ReActAgent` 的运行配置与便捷配置方法。 |
| [`ReActAgentEvolve`](./agents/ReActAgentEvolve.md) | 在 ReAct 基础上引入可演进 operator 的实现。 |

## 说明

- 相关测试：`AgentCallbackManagerTest`、`ReActAgentConfigTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`。
