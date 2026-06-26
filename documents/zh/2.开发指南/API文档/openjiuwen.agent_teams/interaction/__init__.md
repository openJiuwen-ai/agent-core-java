# openjiuwen.agent_teams.interaction

对应 Python 文件：`openjiuwen/agent_teams/interaction/__init__.py`

`InteractionPackage` 是 Java 侧的 interaction 包门面，用来记录 Python `__all__` 的公开导出顺序。0.1.14 版本在原有人类输入和用户输入入口之外，新增了 bridge-agent 协议与 payload 相关导出。

Java 对应类型：

- `com.openjiuwen.agent_teams.interaction.InteractionPackage`

主要行为：

- `PYTHON_MODULE` 指向完整 Python 相对路径。
- `EXPORTED_SYMBOLS` 保留 Python `__all__` 顺序。
- 已翻译的 bridge protocol / payload 类型通过 Class 常量暴露。
- `HumanAgentInbox`、`UserInbox` 和 router 函数由后续对应任务翻译，本包门面只保留导出名，不提前实现子模块逻辑。
