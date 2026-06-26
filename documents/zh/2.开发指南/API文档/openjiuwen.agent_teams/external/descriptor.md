# openjiuwen.agent_teams.external.descriptor

对应 Python 文件：`openjiuwen/agent_teams/external/descriptor.py`

`TeamJoinDescriptor` 描述外部 agent 加入团队所需的连接信息，包括 session、team、member、角色、语言、数据库配置和消息传输配置。`TEAM_JOIN_ENV` 是承载 JSON descriptor 的环境变量名。

Java 对应类型：

- `com.openjiuwen.agent_teams.external.TeamJoinDescriptor`

主要行为：

- `toJson()` 输出紧凑 JSON。
- `toEnv()` 返回包含 `OPENJIUWEN_TEAM_JOIN` 的环境变量映射。
- `fromJson(String)` 解析 JSON，并在格式错误或缺少必需字段时抛出团队配置错误。
- `fromEnv(Map<String, String>)` 从环境变量映射读取 descriptor，缺失变量时抛出团队配置错误。
