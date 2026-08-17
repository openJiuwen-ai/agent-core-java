# 高阶用法

本栏目整理 Java 版已经具备、但通常需要跨多个子系统一起理解的能力：检索、记忆、上下文、安全护栏、执行与恢复、Session、Skills、系统操作、提示词优化、组件扩展以及 agent evolving。每一页都只以 Java 当前公开 API、示例和测试覆盖范围为准。

## 页面分组

### 检索 / 记忆 / 上下文 / 安全

| 页面 | 关注点 | 主要依据 | 说明 |
| --- | --- | --- | --- |
| [知识检索](知识检索.md) | 知识库、索引、检索与重排 | `com.openjiuwen.core.retrieval`、`examples/retrieval` | 以 Java retrieval API 和示例入口为准。 |
| [记忆引擎](记忆引擎.md) | 长期记忆、作用域配置、记忆检索 | `com.openjiuwen.core.memory` | 结合 memory 根包与管理子包理解。 |
| [上下文引擎](上下文引擎.md) | `ContextEngine`、上下文窗口、处理器与状态 | `com.openjiuwen.core.context`、`examples/context_evolver` | 覆盖上下文生命周期与配置入口。 |
| [安全护栏 Guardrail](安全护栏Guardrail.md) | 风险分析、回调事件与阻断策略 | `com.openjiuwen.core.security.guardrail` | 当前以公开源码和根包 README 为准。 |
| [多租户数据隔离](多租户数据隔离.md) | 租户上下文、工作区/KV/Skill 隔离与清理 | `com.openjiuwen.core.multitenant` | 以多租户开关、传入入口与资源隔离边界为准。 |

### 执行 / 交互 / 恢复

| 页面 | 关注点 | 主要依据 | 说明 |
| --- | --- | --- | --- |
| [执行器 Runner](执行器Runner.md) | 全局运行器、资源管理、执行入口 | `com.openjiuwen.core.runner` | 解释 workflow / agent / group 的统一执行门面。 |
| [人机交互](人机交互.md) | 交互输入、打断、恢复与等待流程 | `session.interaction`、`examples/interact` | 以 Java 当前交互语义为准。 |
| [Checkpointer 检查点机制](Checkpointer检查点机制.md) | 检查点生命周期、恢复、持久化与内存实现 | `session.checkpointer`、`graph` | 与交互恢复配套阅读。 |
| [WorkflowAgent 支持多工作流跳转](WorkflowAgent支持多工作流跳转.md) | 多工作流路由与继续执行 | `examples/workflow_agent`、`application.workflow` | 以 Java 当前多 workflow 示例和入口能力为准。 |

### Session 子目录

- [执行期（Session）](Session/README.md)
- 该子目录会继续拆分 `概述`、`中断恢复`、`流式输出`、`状态管理`、`调测能力` 五页。
- 主要依据：`com.openjiuwen.core.session` 根包及其 `interaction`、`checkpointer`、`stream`、`state`、`tracer`、`callback` 子包。

### 自动化开发交付

| 页面 | 关注点 | 主要依据 | 说明 |
| --- | --- | --- | --- |
| [GitCode Feature Evolver](GitCode%20Feature%20Evolver.md) | Feature Issue、Controller、ReAct、测试 Gate、双 PR 和 System Test 交付 | `examples/gitcode_feature_evolver`、`resources/skills/gitcode-feature-devflow` | 说明从 Feature Issue 到 Feature PR、System Test PR 合入的完整持久化流程。 |

### Skills / 系统操作 / 扩展 / 演化

| 页面 | 关注点 | 主要依据 | 说明 |
| --- | --- | --- | --- |
| [Agent Skills](Agent%20Skills.md) | 本地技能、远程技能、技能注册与提示词拼装 | `singleagent.skills`、`examples/skill_create`、`examples/skill_use` | 聚焦 Java 当前技能系统。 |
| [系统操作](系统操作.md) | `SysOperation`、本地/沙箱模式、结果模型 | `com.openjiuwen.core.sysop` | 解释系统操作门面和运行模式。 |
| [技能与系统操作](技能与系统操作.md) | 技能与 sysop 的组合关系 | `singleagent.skills`、`sysop`、`examples/skill_use` | 强调工具适配与运行边界。 |
| [生成和优化提示词](生成和优化提示词.md) | 运行时 prompt 与离线生成/优化工具的边界 | `foundation.prompt`、相关扩展能力 | 会与 `基础功能/填充提示词模板` 做明确分工。 |
| [开发自定义组件](开发自定义组件.md) | 基于 `WorkflowComponent` 扩展组件 | `workflow.component` | 聚焦组件扩展，不重复基础工作流教程。 |
| [回调框架](异步回调框架.md) | callback framework、事件流和观察点 | `runner.callback`、`session.callback` | 以 Java 现有回调框架为准。 |
| [ReActAgent演化训练](ReactAgent强化学习.md) | 演化训练、评估与优化流程 | `ReActAgentEvolve`、`examples/agent_evolving` | 聚焦 Java 当前可运行的演化训练主线。 |
| [自优化Agent](自优化Agent.md) | 训练闭环、优化器、评估器与 checkpoint | `ReActAgentEvolve`、`InstructionOptimizer`、`DefaultEvaluator`、`Trainer` | 聚焦 Java 当前的自演化闭环。 |

## 阅读提示

- 先根据问题类型选择分组，再进入具体页面。
- 如果你需要先理解执行期会话语义，优先从 [执行器 Runner](执行器Runner.md)、[人机交互](人机交互.md)、[Checkpointer 检查点机制](Checkpointer检查点机制.md) 和 [执行期（Session）](Session/README.md) 开始。
- 涉及技能、系统操作或演化训练的页面，都会显式标出当前 Java 能力状态和实现边界。

## 参考入口

- [API 文档：retrieval](../API文档/com.openjiuwen.core/retrieval.README.md)
- [API 文档：memory](../API文档/com.openjiuwen.core/memory.README.md)
- [API 文档：context](../API文档/com.openjiuwen.core/context.README.md)
- [API 文档：session](../API文档/com.openjiuwen.core/session.README.md)
- [API 文档：runner](../API文档/com.openjiuwen.core/runner.README.md)
- [API 文档：singleagent](../API文档/com.openjiuwen.core/singleagent.README.md)
- [API 文档：sysop](../API文档/com.openjiuwen.core/sysop.README.md)
- [API 文档：security](../API文档/com.openjiuwen.core/security.README.md)
- [示例：retrieval](../../../../examples/retrieval/README.md)
- [示例：interact](../../../../examples/interact/README.md)
- [示例：context_evolver](../../../../examples/context_evolver/README.md)
- [示例：skill_create](../../../../examples/skill_create/README.md)
- [示例：skill_use](../../../../examples/skill_use/README.md)
- [示例：agent_evolving](../../../../examples/agent_evolving/README.md)
