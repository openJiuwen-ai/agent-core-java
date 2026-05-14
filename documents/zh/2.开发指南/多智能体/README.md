# 多智能体

本栏目按 Java 当前已经公开的 group、session、resource manager 和示例层能力组织内容。当前 Java 侧的推荐主线是 `BaseGroup + GroupCard + GroupConfig + MultiAgentSessions`；legacy `ControllerGroup` / `BaseGroupController` 主要用于兼容说明和示例映射。

## 页面映射

| 页面 | Java 对应主线 | 主要依据 | 说明 |
| --- | --- | --- | --- |
| [概述](概述.md) | 多智能体整体模型与阅读顺序 | `com.openjiuwen.core.multiagent`、`examples/groups` | 先建立 Java 侧术语与推荐路径。 |
| [BaseGroup与组封装](BaseTeam.md) | `BaseGroup`、`GroupCard`、`GroupConfig` | `BaseGroup`、`GroupConfig`、`schema` 子包 | 聚焦 Java 的 `BaseGroup` 主线。 |
| [组运行时职责与通信协作](TeamRuntime与CommunicableAgent.md) | 运行时职责分配与通信协作 | `MultiAgentSessions`、`AgentGroupSessionApi`、resource manager、legacy controller group | 说明 group、session、controller 和 resource manager 的职责边界。 |
| [AgentAsTool](AgentAsTool.md) | 把 Agent 当作 Tool 使用 | `AbilityManager`、`Runner.resourceMgr()`、`ReActAgent` | 面向使用者的接入教程，说明怎样把子 Agent 暴露成宿主 Agent 的可调用能力。 |
| [团队装配与运行](AgentTeams.md) | 团队装配与协作入口 | `multiagent` API、`examples/groups` | 以 Java 当前可组合的 group/session 能力为准。 |
| [预置协作模式](预置协作模式.md) | 常见协作结构与示例映射 | `examples/groups` | 重点说明 Java 现有示例能表达的协作模式和边界。 |

## 阅读提示

- 先完成 `概述`、`BaseTeam` 和 `TeamRuntime与CommunicableAgent`，再进入能力暴露和协作模式页面。
- 如果你更关心“现在仓库里有哪些真实入口”，优先结合 `examples/groups` 与 `API文档/com.openjiuwen.core/multiagent.README.md` 一起阅读。
- 对于采用兼容层或示例层实现的主题，文档会显式标注当前定位和使用边界。

## 参考入口

- [API 文档：multiagent](../API文档/com.openjiuwen.core/multiagent.README.md)
- [示例：Groups Java Examples](../../../../examples/groups/README.md)

## 当前能力边界

- `examples/groups` 当前主要提供示例层的协作样例，用于帮助理解能力组合方式。
- 这里会区分“推荐的公共 API 路径”和“兼容/示例层路径”，避免把 legacy 或 bridge 实现直接理解成正式抽象。
