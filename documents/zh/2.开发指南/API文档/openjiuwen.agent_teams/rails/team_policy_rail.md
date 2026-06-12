# openjiuwen.agent_teams.rails.team_policy_rail

该模块对应 Python `openjiuwen.agent_teams.rails.team_policy_rail`，负责把团队角色、流程、生命周期、人物设定、团队信息和成员关系注入共享 `SystemPromptBuilder`。

## Java 对应

- `com.openjiuwen.agent_teams.rails.TeamPolicyRail`

## 行为

- `PRIORITY` 为 12，对应 Python `priority = 12`。
- 构造时通过 `Config` 生成静态 section：`team_role`、`team_bridge`、`team_workflow`、`team_lifecycle`、`team_persona`、`team_extra`。
- `beforeModelCall(...)` 每次模型调用前重新加入静态 section。
- 当配置了 `TeamBackend` 时，动态刷新 `team_hitt`、`team_members` 和 `team_info`。
- `team_info` 使用 `MtimeSectionCache` 通过 team mtime 避免重复读取。
- `team_hitt` 和 `team_members` 共用 members mtime，一次探测决定是否重新读取成员和人类成员列表。
- `uninit(...)` 移除本 rail 拥有的静态和动态 section。

Java 侧当前使用 `PolicyAgent`、`TeamBackend`、`TeamInfoSnapshot`、`TeamMemberSnapshot` 和 `PolicyCallbackContext` 作为窄接口，表达 Python 中通过 `Any`、`TeamBackend` 和 `AgentCallbackContext` 动态访问的最小行为面；待 `DeepAgentRail` 与 `AgentCallbackContext` 翻译完成后可进一步接入正式 rail base。
