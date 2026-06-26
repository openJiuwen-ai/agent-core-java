# 使用组件

本子目录聚焦 Java 工作流的组件层能力：哪些组件可以直接拿来搭图、哪些类型只是底层契约或辅助壳、以及如何把这些组件真正编排成一条可运行的 workflow。这里不重复工作流总体概念，默认你已经先阅读过上一级栏目中的 [概述](../概述.md)、[关键概念](../关键概念.md) 或 [构建工作流](../构建工作流.md)。

如果你正在排查图结构、分支连线或子图展开，也建议结合 [工作流可视化](../工作流可视化.md) 一起阅读。

## 先建立两个层次

阅读 Java 工作流组件时，建议先把类型分成两层：

### 第一层：直接拿来搭图的预置组件

这类类型通常会直接出现在 `setStartComp(...)`、`addWorkflowComp(...)`、`setEndComp(...)` 这类调用里，例如：

- `Start`
- `End`
- `LLMComponent`
- `QuestionerComponent`
- `IntentDetectionComponentImpl`
- `KnowledgeRetrievalComponent`
- `ToolComponent`
- `SubWorkflowComponentImpl`
- `LoopComponentImpl`

如果你的目标是先把 workflow 搭起来，应优先从这些类型开始。

### 第二层：支撑这些组件工作的契约、配置和底层抽象

这类类型很重要，但它们通常不是“第一步就直接 new 出来跑”的入口，例如：

- `ComponentAbility`
- `IOConfig`
- `ComponentConfig`
- `NodeConfig`
- `ComponentState`
- `WorkflowComponentMetadata`
- `BranchComponent`
- `LoopComponent` / `AdvancedLoopComponent`
- `SubWorkflowComponent`

这层更适合在你准备写自定义组件、理解能力模型或排查复杂编排时再深入。

## 页面映射

| 页面 | 关注点 | 主要 Java 依据 | 说明 |
| --- | --- | --- | --- |
| [使用预置组件](使用预置组件.md) | 认识 `Start`、`End`、`IntentDetectionComponent` 等现成组件 | `com.openjiuwen.core.workflow.component` | 先理解公开稳定组件，再进入自定义和编排。 |
| [定义组件输入输出格式](定义组件输入输出格式.md) | `IOConfig`、`NodeConfig`、配置与输入输出壳类型 | `IOConfig`、`ComponentConfig`、`NodeConfig` | 解释 Java 当前组件 I/O 的组织方式。 |
| [组件支持的能力类型](组件支持的能力类型.md) | `ComponentAbility` 与执行模式 | `ComponentAbility`、`WorkflowComponent`、`ComponentExecutable` | 区分 `invoke`、`stream`、`collect`、`transform` 等能力。 |
| [编排组件](编排组件.md) | 连边、子工作流、循环与组合方式 | `Workflow`、`SubWorkflowComponentImpl`、`HasDrawable` | 以 Java 当前图模型和组件接口为准。 |

## 按 Java 当前子包来看组件

| 包或目录 | 代表类型 | 更适合什么时候读 |
| --- | --- | --- |
| `component` 根包 | `Start`、`End`、`BranchComponent`、`SubWorkflowComponentImpl` | 先搭最小 workflow，理解起止节点、条件路由和子工作流入口。 |
| `component.llm` | `LLMComponent`、`QuestionerComponent`、`IntentDetectionComponentImpl` | 需要接大模型、做意图识别或在执行中补问用户。 |
| `component.resource` | `KnowledgeRetrievalComponent` | 需要把知识检索结果作为 workflow 中间节点能力。 |
| `component.tool` | `ToolComponent` | 需要把 Java `Tool` 体系接入 workflow。 |
| `component.loop` | `LoopComponentImpl`、`AdvancedLoopComponentImpl` | 需要把一段子图反复执行，或按循环条件控制流程。 |

这个分类和 Java 当前源码、API 文档的子包结构一致，适合作为后续扩展页面的统一入口。

## 阅读提示

- `使用预置组件` 适合先建立“现成组件能做什么”的整体印象。
- `定义组件输入输出格式` 与 `组件支持的能力类型` 适合在开始写自定义组件前阅读。
- `编排组件` 会把组件放回完整工作流图里讨论，不单独脱离 `Workflow` 讲解。
- `工作流可视化` 可以作为配套页，用来检查这些组件最终在图上是如何呈现的。

## 参考入口

- [工作流可视化](../工作流可视化.md)
- [API 文档：workflow.component](../../API文档/com.openjiuwen.core/workflow/component.README.md)
- [API 文档：workflow](../../API文档/com.openjiuwen.core/workflow.README.md)
- [示例：Workflow Agent Java Example](../../../../../examples/workflow_agent/README.md)

## 当前能力边界

- 子目录正文会以 `com.openjiuwen.core.workflow.component` 当前公开类型为主线。
- `使用预置组件` 只把可以直接消费的组件类型当作主清单，不把 `*Executable`、`Template*`、`*Input` / `*Output`、状态类或 helper 当成推荐入口。
- `workflow.components` 这类兼容布局只会在需要说明历史兼容性时提及，不会混成当前主推荐写法。
