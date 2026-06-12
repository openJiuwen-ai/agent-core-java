# openjiuwen.agent_teams.memory.extractor

对应 Python 文件：`openjiuwen/agent_teams/memory/extractor.py`

`TeamMemoryExtractor` 负责把团队任务和消息整理成可交给记忆提取 agent 的上下文，并提供受限的 team-memory 文件工具。

Java 对应类型：

- `com.openjiuwen.agent_teams.memory.TeamMemoryExtractor`
- `TeamMemoryExtractor.ExtractionRequest`
- `TeamMemoryExtractor.ExtractionTool`
- `TeamMemoryExtractor.FileSystemView`
- `TeamMemoryExtractor.AgentFactory`
- `TeamMemoryExtractor.RunnerView`

主要行为：

- `buildExtractionContext(tasks, messages, timezoneOffsetHours)` 生成团队协作记录上下文。
- `createExtractionTools(teamMemoryDir, fileSystem, teamName)` 创建 `extract.<team>.read/write/list` 工具描述。
- `readMemoryFile`、`writeMemoryFile`、`listMemoryFiles` 只允许访问 team-memory 目录下的 basename 文件，拒绝 `..` 和绝对路径。
- `extractTeamMemories(request)` 在缺少 sysOperation、teamMemoryDir、model、db 或 taskManager 时直接返回；运行错误只记录为 no-op，不向调用方传播。

实现说明：

- Python 的 `create_deep_agent` 和 `Runner.run_agent` 在 Java 中用 `AgentFactory`、`RunnerView` 注入，避免提前实现未到当前任务范围的 harness/runner 依赖。
- 任务正文预览上限为 2000 字符，消息正文预览上限为 1000 字符，agent 最大迭代次数为 5。
