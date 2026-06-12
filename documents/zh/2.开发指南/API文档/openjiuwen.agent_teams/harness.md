# openjiuwen.agent_teams.harness

对应 Python 文件：`openjiuwen/agent_teams/harness.py`

`TeamHarness` 是 `TeamAgent` 与底层 DeepAgent 之间的唯一运行时适配层。Java 版本通过 `MemberRuntime` 暴露同一组业务调用面，并把尚未完全静态建模的 DeepAgent / Runner 运行时保留在可替换的适配边界内。

Java 对应类型：

- `com.openjiuwen.agent_teams.TeamHarness`
- `com.openjiuwen.agent_teams.TeamHarness.MountedRails`
- `com.openjiuwen.agent_teams.TeamHarness.StreamingRunner`
- `com.openjiuwen.agent_teams.TeamHarness.SimpleAgentSession`

主要行为：

- `build(...)` 按 Python 的挂载顺序添加 team tool、policy、first-iteration gate、workspace、approval 和 team-plan rail。
- team tool rail 在 policy rail 挂载前执行 `setSysOperation`、`setWorkspace` 和 `init`，保持 Python 中“能力快照先就绪”的顺序。
- `runStreaming(...)` 通过可注入 `StreamingRunner` 承接 Python `Runner.run_agent_streaming` 的调用位置。
- `hasPendingInterrupt()` 和 `isPendingInterruptResumeValid(...)` 使用 `__react_agent_interruption__` 状态检查 pending interrupt。
- `initCwdForRound()` 在 workspace 存在时调用 Java `Cwd.initCwd` 初始化本轮工作目录。
- `registerMemberTools(...)`、`injectMemberMemory(...)` 和 `runAgentCustomizer(...)` 只通过内部 DeepAgent 适配边界转发。

实现说明：

- Java 运行时中 DeepAgent、Runner 和部分 rail 类型尚未全部收敛为强类型，因此本类在边界处使用反射适配 camelCase / snake_case 方法名；该动态范围限制在 harness 适配层内部。
- `StreamingRunner.empty()` 是默认空流实现，测试和未来真实 Runner 翻译可替换该 seam。
