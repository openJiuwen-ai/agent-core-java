# rail

`com.openjiuwen.core.single_agent.rail` 定义单智能体生命周期中的 rail 钩子、回调事件、事件载荷与重试辅助类型。

## 类型

| 类型 | 说明 |
|---|---|
| [`AgentCallback`](./rail/AgentCallback.md) | 以 `Consumer<AgentCallbackContext>` 表达的回调函数接口。 |
| [`AgentCallbackContext`](./rail/AgentCallbackContext.md) | 在 rail 与 callback 之间传递事件、输入、异常与重试信息的上下文对象。 |
| [`AgentCallbackEvent`](./rail/AgentCallbackEvent.md) | 单智能体生命周期中的标准回调事件枚举。 |
| [`AgentCallbackFirer`](./rail/AgentCallbackFirer.md) | 供 `AgentCallbackContext` 触发事件时调用的分发接口。 |
| [`AgentRail`](./rail/AgentRail.md) | 定义 8 个生命周期 hook、优先级与自动注册工具列表的基类。 |
| [`EventInputs`](./rail/EventInputs.md) | 所有事件输入载荷的标记接口。 |
| [`InvokeInputs`](./rail/InvokeInputs.md) | 用于 `BEFORE_INVOKE` / `AFTER_INVOKE` 的输入载荷。 |
| [`ModelCallInputs`](./rail/ModelCallInputs.md) | 用于模型调用阶段的消息、工具与响应载荷。 |
| [`RailExecutor`](./rail/RailExecutor.md) | 负责包装 rail 生命周期事件并处理重试的执行工具。 |
| [`RetryRequest`](./rail/RetryRequest.md) | 由异常 rail 产生的重试指令。 |
| [`ToolCallInputs`](./rail/ToolCallInputs.md) | 用于工具调用阶段的请求与结果载荷。 |

## 说明

- 相关测试：`AgentCallbackManagerTest`、`AgentCallbackContextTest`、`AgentCallbackEventTest`、`AbilityManagerSupplementTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`DataClassCoverageTest`、`AgentRailTest`、`RailExecutorTest`、`RailDataClassesTest`。
