# multiagent

`com.openjiuwen.core.multi_agent` 提供新版多 Agent 分组抽象、运行时配置，以及顶层会话门面。

## Modules

| 模块 | 说明 |
|---|---|
| [`legacy`](./multiagent/legacy.README.md) | 已废弃的 `ControllerGroup` / `GroupEvent` / legacy schema 兼容层。 |
| [`schema`](./multiagent/schema.README.md) | `GroupCard` 与 `EventDrivenGroupCard` 等分组身份模型。 |

## Types

| 类型 | 说明 |
|---|---|
| [`BaseGroup`](./multiagent/BaseGroup.md) | 基于 Card + Config 模式的抽象分组基类，负责 Agent 注册、配置与执行入口。 |
| [`GroupConfig`](./multiagent/GroupConfig.md) | 分组运行时限制、并发上限与超时配置。 |
| [`MultiAgentSessions`](./multiagent/MultiAgentSessions.md) | 在 `multiagent` 包内创建 `Session` 的静态便捷门面。 |
| [`Session`](./multiagent/Session.md) | `AgentGroupSessionApi` 的包级别别名，保留 `com.openjiuwen.core.session.Session` 这一导入入口。 |

## Notes

- 新版分组通过 `GroupCard` 描述身份，通过 `GroupConfig` 描述运行参数。
- `MultiAgentFacadeTest` 验证了 `Session` 与 `MultiAgentSessions.createAgentGroupSession(...)` 的会话 ID / env 透传行为。
