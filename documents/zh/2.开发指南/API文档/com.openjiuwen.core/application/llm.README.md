# llm

`com.openjiuwen.core.application.llm` 提供 ReAct 风格应用层 Agent、控制器封装与任务中断状态对象。

## Types

| 类型 | 说明 |
|---|---|
| [`LlmAgent`](./llm/LlmAgent.md) | 基于 `ControllerAgent` 的 ReAct Agent，负责会话管理、流式输出与可选的长期记忆写回。 |
| [`LlmController`](./llm/LlmController.md) | 用于绑定 `LlmEventHandler`、归一化输入并访问控制器行为的辅助类。 |
| [`LlmEventHandler`](./llm/LlmEventHandler.md) | 执行 LLM 规划、插件/工作流调用、恢复中断与最终流输出的核心事件处理器。 |
| [`TaskInterruptionState`](./llm/TaskInterruptionState.md) | 封装任务中断现场的状态载体。 |

## Notes

- 本包文档以 `LlmAgent.java`、`LlmController.java`、`LlmEventHandler.java` 与应用层回归测试为准。
- 页面重点记录公开入口以及可从源码稳定导出的行为。
