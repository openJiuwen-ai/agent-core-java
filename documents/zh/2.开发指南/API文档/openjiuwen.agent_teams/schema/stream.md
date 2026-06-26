# openjiuwen.agent_teams.schema.stream

Java 对应包：`com.openjiuwen.agent_teams.schema`

该模块提供团队层流式输出扩展。非团队生产者继续使用核心 `OutputSchema`；团队成员产生的 chunk 可以使用 `TeamOutputSchema` 标记来源成员和角色。

## TeamOutputSchema

`TeamOutputSchema` 继承 `com.openjiuwen.core.session.stream.OutputSchema`，额外包含：

- `sourceMember`：产生该 chunk 的团队成员名
- `role`：该成员的 `TeamRole`

`sourceMember` 和 `role` 可以为 `null`，用于兼容普通单 Agent 或 harness 直接输出。

## fromOutput

`TeamOutputSchema.fromOutput(base, sourceMember, role)` 会复制 `base` 的：

- `type`
- `index`
- `payload`

然后返回新的 `TeamOutputSchema`。原始 `OutputSchema` 不会被修改。

## Python 对应关系

该 Java 实现对应 Python 源文件：

`openjiuwen/agent_teams/schema/stream.py`
