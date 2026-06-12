# openjiuwen.agent_teams.prompts.policy

`PromptPolicy` 对应 Python `policy.py`，负责按团队角色、语言、生命周期和团队上下文组装系统 prompt。

## Java 对应

- `com.openjiuwen.agent_teams.prompts.PromptPolicy`

## 方法

- `rolePolicy(TeamRole role, String language)`：读取 leader 或 teammate 的基础策略模板。
- `buildSystemPrompt(...)`：组装成员名、角色策略、leader 工作流、生命周期说明、人设、团队信息、成员关系和额外 base prompt。

`teamInfo` 与 `teamMembers` 使用 `Map` 输入，是为了保持 Python `dict` 传参边界；最终返回值是具体 `String` prompt。
