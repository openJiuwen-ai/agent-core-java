# openjiuwen.agent_teams.agent.session_manager

## SessionManager

`SessionManager` 管理 TeamAgent 的 session 绑定、释放和 session 切换恢复，对应 Python 源文件 `openjiuwen/agent_teams/agent/session_manager.py`。

Java 侧核心行为：

- `bindSession` 设置 `AgentTeamsContext` 中的 session id，并把 session 写入 state。
- 再次绑定前会先重置上一次 `setSessionId` 返回的 token，避免 session id 泄漏到后续上下文。
- `releaseSession` 重置 token 并清空 live `teamSession`。
- `resumeForNewSession` 在新 session 下重新绑定 leader 侧可恢复 teammate，并先执行 cleanup。
- `recoverForExistingSession` 面向 checkpoint 恢复，重新绑定但不先 cleanup。

`AgentTeamSessionView` 和 `TeamAgentStateView` 是本翻译单元使用的窄接口，用于对齐 Python 中 `AgentTeamSession` 和 `TeamAgentState.team_session` 的最小访问面。
