# openjiuwen.agent_teams.agent.state

## TeamAgentState

`TeamAgentState` 是 TeamAgent 各 operator 共享的可变运行时状态容器，对应 Python 源文件 `openjiuwen/agent_teams/agent/state.py`。

Java 侧字段默认值与 Python dataclass 保持一致：

- `teamSession`: 默认 `null`。
- `teamMember`: 默认 `null`。
- `pendingUserQuery`: 默认空字符串。
- `eventListeners`: 默认独立的空列表。
- `teamCleaned`: 默认 `false`。

session id 不放在该状态对象中，当前 session id 仍以 `AgentTeamsContext` 为唯一来源。
