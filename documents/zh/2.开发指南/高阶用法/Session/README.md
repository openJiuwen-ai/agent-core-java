# 执行期（Session）

本子目录从 Java 当前公开的 `com.openjiuwen.core.session` 子系统出发，组织“会话对象是什么、执行如何在同一会话里继续、恢复为什么依赖同一个 `sessionId`”这三类问题。它与高阶用法根目录中的 [执行器 Runner](../执行器Runner.md)、[人机交互](../人机交互.md)、[Checkpointer 检查点机制](../Checkpointer检查点机制.md) 互补：根目录讲跨子系统运行链路，这里专门讲 Session 自身的对象模型和恢复边界。

## 当前页面

| 页面 | 关注点 | 主要 Java 依据 |
| --- | --- | --- |
| [概述](概述.md) | Session 的角色、核心类型和子系统分工 | `com.openjiuwen.core.session` |
| [中断恢复](中断恢复.md) | `InteractiveInput`、检查点与同一 `sessionId` 的恢复语义 | `session.interaction`、`session.checkpointer`、`workflow_agent` 测试 |
| [流式输出](流式输出.md) | `stream mode`、chunk、writer、queue / emitter 以及与 workflow / agent 流式接口的关系 | `session.stream`、`Workflow.stream(...)`、`Runner.run*Streaming(...)` |
| [状态管理](状态管理.md) | 会话状态分区、读写生命周期、提交 / 回滚与恢复边界 | `session.state`、`session.checkpointer` |
| [调测能力](调测能力.md) | callback、tracer、trace stream 与自定义调试入口 | `session.callback`、`session.tracer`、`session.stream` |

## 阅读顺序

1. 先看 [高阶用法总览](../README.md)，确认 Session 在整个栏目中的位置。
2. 再看 [概述](概述.md)，先把 `BaseSession`、`AgentSessionApi`、`WorkflowSessionApi`、`ProxySession` 的关系理清。
3. 如果你关心补问、失败重试或继续执行，再读 [中断恢复](中断恢复.md)。
4. 如果你关心流块是怎么发出来、怎样被 `workflow.stream(...)` 或 `Runner.runAgentStreaming(...)` 消费，再读 [流式输出](流式输出.md)。
5. 如果你关心状态为什么分成全局 / 组件 / workflow 几层，以及提交 / 回滚发生在什么时候，再读 [状态管理](状态管理.md)。
6. 如果你要做 trace 观测、挂 callback handler，或向组件注入调试信息，再读 [调测能力](调测能力.md)。

## 和高阶用法根目录的分工

- [人机交互](../人机交互.md) 重点是交互事件和 `InteractiveInput` 协议。
- [Checkpointer 检查点机制](../Checkpointer检查点机制.md) 重点是生命周期、命名空间和持久化键结构。
- 本子目录重点是“这些能力为什么都挂在同一个 Session 边界上”，以及恢复时为什么必须回到同一个会话。

## 专题之间的关系

- [中断恢复](中断恢复.md) 解释“为什么必须回到同一个 `sessionId` 才能继续执行”。
- [流式输出](流式输出.md) 解释“执行中的结果、交互与 trace 为什么会从同一条 Session 流管道出去”。
- [状态管理](状态管理.md) 解释“Session 内部状态为什么不是一个普通 `Map`，以及何时提交、回滚、恢复”。
- [调测能力](调测能力.md) 解释“callback 与 tracer 如何把这些运行时事件变成可观察的数据”。

## 参考入口

- [API 文档：session](../../API文档/com.openjiuwen.core/session.README.md)
- [API 文档：session.interaction](../../API文档/com.openjiuwen.core/session/interaction.README.md)
- [API 文档：session.checkpointer](../../API文档/com.openjiuwen.core/session/checkpointer.README.md)
- [测试：WorkflowAgent 流式中断恢复](../../../../../src/test/java/com/openjiuwen/core/application/workflow_agent/WorkflowAgentInterruptStreamMissingTest.java)
