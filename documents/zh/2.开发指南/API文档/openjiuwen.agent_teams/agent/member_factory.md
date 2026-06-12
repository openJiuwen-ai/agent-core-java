# openjiuwen.agent_teams.agent.MemberFactory

`MemberFactory` 对应 Python 模块 `openjiuwen/agent_teams/agent/member_factory.py`，集中构造每个角色持有的 `TeamMember` handle。

## 主要能力

- `createMemberHandle`：当 `TeamInfra` 尚未绑定 team backend 时返回 `null`，保留 Python `create_member_handle` 返回 `None` 的语义。
- backend 已绑定时，使用 backend 的 `teamName`、member store、`infra.messager`、传入的 `AgentCard` 和 blueprint context 的 persona 构造 `TeamMember`。
- 工厂本身不读取或写入数据库；leader 自身 DB row 尚未 materialize 时也可以先构造 handle，后续读写由 `TeamMember` 处理 missing-row no-op。

## 兼容说明

当前 Java `ConfiguredTeamBackend` 增加了可选 `MemberStore` 字段，用于承接 Python `infra.team_backend.db`。旧构造函数保持兼容，后续数据库聚合门面翻译完成后可通过适配器传入具体 store。
