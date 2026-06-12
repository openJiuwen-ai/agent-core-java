# openjiuwen.agent_teams.prompts.sections

`TeamPromptSections` 对应 Python `sections.py`，负责为团队策略 rail 构造可排序的 `PromptSection`。

## Java 对应

- `com.openjiuwen.agent_teams.prompts.TeamPromptSections`
- `TeamPromptSections.TeamSectionName`

## 方法

- `buildTeamRoleSection(...)`：构造团队角色、成员名和 teammate 执行模式说明。
- `buildTeamWorkflowSection(...)`：仅 leader 角色返回工作流 section。
- `buildTeamLifecycleSection(...)`：仅 leader 角色返回生命周期 section。
- `buildTeamPersonaSection(...)` / `buildTeamExtraSection(...)`：按 Python 空值规则返回可选 section。
- `buildTeamInfoSection(...)`：输出团队标识、展示名、目标和共享工作空间信息。
- `buildTeamHittSection(...)`：按 leader、teammate、human_agent 角色生成 HITT 协作规则。
- `buildTeamBridgeSection(...)`：按 leader、teammate、bridge_agent 角色生成桥接外部 agent 协作规则。
- `buildTeamMembersSection(...)`：输出成员关系，并排除当前成员。
- `buildTeamStaticSections(...)` / `buildTeamMemberSystemPrompt(...)`：按 Python 顺序组装静态团队 section 并渲染独立系统 prompt。

`teamInfo` 与成员列表保留 `Map` 输入，是为了对齐 Python `dict`/数据库行边界；公开返回值使用 `PromptSection`、`Optional<PromptSection>`、`List<PromptSection>` 和 `String`。
