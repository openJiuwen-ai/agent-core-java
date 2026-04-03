# workflow

`com.openjiuwen.core.workflow` 汇总工作流图构建、运行时模型、路由能力、直属组件、条件表达式以及新版与兼容版组件族。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`component`](./workflow/component.README.md) | 提供当前工作流运行时使用的直属组件契约、节点配置 DTO、输入输出模型与辅助抽象。 |
| [`components`](./workflow/components.README.md) | 保留旧版工作流组件包布局，供兼容历史文档与集成场景使用。 |
| [`condition`](./workflow/condition.README.md) | 提供表达式、数组、函数、会话值与数值谓词等可复用工作流条件。 |
| [`internal`](./workflow/internal.README.md) | 收录工作流组件层内部使用的兼容辅助类型。 |

## Types

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [`BaseWorkflow`](./workflow/BaseWorkflow.md) | `class` | 工作流基础实现，负责图构建、边管理、组件配置与能力推断。 |
| [`BranchRouter`](./workflow/BranchRouter.md) | `class` | 分支路由器，根据分支条件计算目标节点路径。 |
| [`ComponentAbility`](./workflow/ComponentAbility.md) | `enum` | `com.openjiuwen.core.workflow.component.ComponentAbility` 的顶层重导出枚举，供测试与兼容调用使用。 |
| [`ComponentComposable`](./workflow/ComponentComposable.md) | `interface` | 工作流图构建接口，用于区分图构建逻辑与执行逻辑。 |
| [`ComponentExecutable`](./workflow/ComponentExecutable.md) | `class` | 工作流组件执行基类，提供调用、流式、收集与转换四类基础执行模式。 |
| [`ComponentExecutionHelper`](./workflow/ComponentExecutionHelper.md) | `class` | 在完整图之外执行单个工作流组件的辅助工具。 |
| [`ComponentExecutionParams`](./workflow/ComponentExecutionParams.md) | `class` | 组件执行参数封装类型。 |
| [`ConnectionType`](./workflow/ConnectionType.md) | `enum` | 工作流边连接类型。 |
| [`EdgeTopology`](./workflow/EdgeTopology.md) | `class` | 用于工作流能力推断的边拓扑快照。 |
| [`HasDrawable`](./workflow/HasDrawable.md) | `interface` | 具备 `Drawable` 图表示能力的组件接口，适用于循环组件、子工作流组件等嵌套图场景。 |
| [`Workflow`](./workflow/Workflow.md) | `class` | 工作流主类，表示由多个组件组成的有向图，并负责调度数据流与流式输出。 |
| [`WorkflowCard`](./workflow/WorkflowCard.md) | `class` | 工作流元信息卡片，保存描述信息与输入模式。 |
| [`WorkflowChunk`](./workflow/WorkflowChunk.md) | `interface` | 工作流流式输出块的顶层别名接口。 |
| [`WorkflowChunkType`](./workflow/WorkflowChunkType.md) | `enum` | 工作流执行过程中产生的数据块类型。 |
| [`WorkflowComponent`](./workflow/WorkflowComponent.md) | `class` | 同时组合执行与图构建能力的标准组件实现，是自定义工作流组件的常用基类。 |
| [`WorkflowConfig`](./workflow/WorkflowConfig.md) | `class` | 工作流实例配置类型。 |
| [`WorkflowExecutionState`](./workflow/WorkflowExecutionState.md) | `enum` | 工作流执行状态枚举。 |
| [`WorkflowOutput`](./workflow/WorkflowOutput.md) | `class` | 工作流最终输出容器，封装结果数据与执行状态。 |
| [`WorkflowSessions`](./workflow/WorkflowSessions.md) | `class` | 从 `openjiuwen.core.workflow` 包快捷创建工作流会话的门面类型。 |
| [`WorkflowSpec`](./workflow/WorkflowSpec.md) | `class` | 工作流结构的完整规格描述。 |
| [`WorkflowUtils`](./workflow/WorkflowUtils.md) | `class` | 工作流层辅助工具集合。 |

## 说明

- 当前包页汇总了 `workflow` 子树下已文档化的子包入口。
- 当前页面还链接了本包中 21 个直属公开类型页面。
- 代表性的工作流运行时与主要组件流程可参考 `WorkflowTest.java`。
