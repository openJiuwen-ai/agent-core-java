# schema

`com.openjiuwen.core.singleagent.schema` 提供单智能体运行时使用的卡片、结果与结果制品模型。

## 类型

| 类型 | 说明 |
|---|---|
| [`AgentCard`](./schema/AgentCard.md) | 描述 agent 能力入口的卡片模型。 |
| [`AgentResult`](./schema/AgentResult.md) | 表示单智能体任务执行结果的数据模型。 |
| [`Artifact`](./schema/Artifact.md) | 表示 `AgentResult` 中单个结果制品的数据模型。 |

## 说明

- 相关测试：`AbilityManagerSupplementTest`、`AbilityManagerTest`、`AgentCallbackManagerTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`ControllerAgentTest`、`DataClassCoverageTest`、`SchemaTest`。
