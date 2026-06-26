# openjiuwen.agent_teams.rails.first_iteration_gate

该模块对应 Python `openjiuwen.agent_teams.rails.first_iteration_gate`，提供 TeamAgent 首轮任务循环 gate。

## Java 对应

- `com.openjiuwen.agent_teams.rails.FirstIterationGate`

## 行为

- 初始状态为未就绪。
- `waitReady()` 返回一个在 gate 打开时完成的 `CompletionStage<Void>`，对应 Python 的 `await gate.wait()`。
- `beforeTaskIteration(...)` 对应 Python `before_task_iteration(ctx)`，第一次进入任务循环时打开 gate。
- `isReady()` 对应 Python `is_ready` 属性。
- `reset()` 对应 Python `reset()`，在新一轮开始前重新关闭 gate。

当前 Java 侧 `core.single_agent.rail.base` 尚未在本批次推进到 T01143，因此 `beforeTaskIteration(Object)` 保留为反射生命周期边界；待 `AgentCallbackContext` 与 `AgentRail` 正式翻译后可收窄为具体上下文类型。
