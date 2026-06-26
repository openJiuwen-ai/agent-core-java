# openjiuwen.agent_teams.interaction.human_agent_inbox

对应 Python 文件：`openjiuwen/agent_teams/interaction/human_agent_inbox.py`

`HumanAgentInbox` 负责把已经解析好的 human-agent 输入路由到对应目标。它不会重新解析正文中的 `@member`，而是只根据显式 `to` 参数决定走 avatar、广播或点对点消息。

Java 对应类型：

- `com.openjiuwen.agent_teams.interaction.HumanAgentInbox`
- `com.openjiuwen.agent_teams.interaction.HumanAgentNotEnabledError`
- `com.openjiuwen.agent_teams.interaction.UnknownHumanAgentError`

主要行为：

- `send(body)` 在没有 `to` 时驱动对应 human-agent avatar。
- `send(body, "all")` 和 `send(body, "*")` 以 human-agent 身份广播。
- `send(body, member)` 先验证目标成员存在，再发送点对点消息。
- 未注册任何 human-agent 时抛出 `HumanAgentNotEnabledError`。
- 指定未知 sender 时抛出 `UnknownHumanAgentError`。
- avatar 不可用时返回 `DeliverResult.failure("agent_unavailable")`。
- 目标成员不存在时返回 `DeliverResult.failure("unknown_member:<target>")`。

实现说明：

- Java 使用 `TeamBackendView`、`MessageManagerView`、`AgentLookup`、`AgentRuntime` 和 `OnInbound` 小接口表达 Python 中 `TYPE_CHECKING` 的运行时边界。
- router 的通用解析与目标解析逻辑属于 `router.py` 的后续任务，本类只实现 Python human-agent inbox 自身的路由职责。
