# openjiuwen.agent_teams.agent.TeamAgentBlueprint

`TeamAgentBlueprint` 对应 Python 模块 `openjiuwen/agent_teams/agent/blueprint.py`，是 TeamAgent 构造时确定的不可变装配蓝图。

## 字段

- `card`：leader 或当前 agent 的卡片信息。
- `spec`：TeamAgent 的静态配置。
- `ctx`：当前成员运行上下文。
- `rolePolicy`：根据角色和语言解析出的角色策略文本。
- `language`：解析后的运行语言。

## 派生属性

- `role`：来自 `ctx.role`。
- `memberName`：来自 `ctx.memberName`。
- `lifecycle`：来自 `spec.lifecycle`。
- `teamSpec`：来自 `ctx.teamSpec`。

该类只承载静态装配数据；运行期可变状态和资源由其它对象维护。
