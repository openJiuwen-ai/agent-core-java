# openjiuwen.agent_teams.rails

该包门面对应 Python `openjiuwen.agent_teams.rails.__init__`，用于集中声明 TeamAgent rail 子模块导出的符号。

## Java 对应

- `com.openjiuwen.agent_teams.rails.TeamRailsPackage`

## 导出信息

- `PYTHON_MODULE` 保存 Python 相对路径。
- `EXPORTED_SYMBOLS` 按 Python `__all__` 的顺序保存导出符号。
- `exports(String symbol)` 可检查符号是否属于导出集合。

具体 first-iteration gate、policy rail、plan-mode rail、tool rail 和 approval rail 的行为由各自 Python 文件对应的后续 Java 翻译任务实现。
