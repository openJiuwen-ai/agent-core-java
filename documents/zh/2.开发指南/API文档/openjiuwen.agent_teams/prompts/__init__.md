# openjiuwen.agent_teams.prompts

该包门面对应 Python `openjiuwen.agent_teams.prompts.__init__`，用于集中声明 prompt 子模块导出的符号。

## Java 对应

- `com.openjiuwen.agent_teams.prompts.PromptsPackage`

## 导出信息

- `PYTHON_MODULE` 保存 Python 相对路径。
- `EXPORTED_SYMBOLS` 按 Python `__all__` 的顺序保存导出符号。
- `exports(String symbol)` 可检查符号是否属于导出集合。

具体 prompt loader、policy、sections、team plan agent 和 team plan mode 的行为由各自 Python 文件对应的后续 Java 翻译任务实现。
