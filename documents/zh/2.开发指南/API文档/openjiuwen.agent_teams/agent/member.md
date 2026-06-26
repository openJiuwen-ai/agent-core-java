# openjiuwen.agent_teams.agent.TeamMember

`TeamMember` 对应 Python 模块 `openjiuwen/agent_teams/agent/member.py`，负责成员状态读取、成员状态更新、执行状态更新，以及状态变化事件发布。

## 主要能力

- `status` / `executionStatus`：从成员存储中读取当前成员行；成员尚未注册时返回 `null`，保留 Python 的 expected no-op 语义。
- `updateStatus`：成员行不存在时返回 `false`；新旧成员状态相同则直接返回 `true`，不写 DB、不发布事件；DB 更新失败时返回 `false`；DB 更新成功后发布 `MemberStatusChangedEvent`。
- `updateExecutionStatus`：成员行不存在时返回 `false`；DB 更新失败时返回 `false`；DB 更新成功后发布 `MemberExecutionChangedEvent`。
- 状态事件 topic 使用 `TeamTopic.TEAM.build(AgentTeamsContext.getSessionId(), teamName)`，保持 Python `get_session_id()` 和 team topic 格式。

## 存储边界

当前 Java 项目尚未翻译 Python `TeamDatabase` 聚合门面。`TeamMember.MemberStore` 是本模块最小持久化边界，只包含 `member.py` 实际调用的 `getMember`、`updateMemberStatus` 和 `updateMemberExecutionStatus` 三个操作。后续数据库聚合门面补齐后，可用适配器桥接到现有 DAO，不改变 `TeamMember` 的 Python 行为。

## 事件容错

Python 源码在事件发布失败时记录错误并继续返回 `true`。Java 实现保持相同语义：只要 DB 更新成功，messager 发布异常不会回滚状态更新，也不会把返回值改为 `false`。
