# component

`com.openjiuwen.core.workflow.component` 定义当前工作流运行时使用的直属组件契约、节点配置 DTO、输入输出模型与辅助抽象。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`llm`](./component/llm.README.md) | 提供基于 LLM 的工作流组件、提示词与响应辅助类型、问答状态模型及交互执行工具。 |
| [`loop`](./component/loop.README.md) | 提供循环执行组件、循环体容器、输入模型、控制接口与迭代后处理辅助类型。 |
| [`resource`](./component/resource.README.md) | 提供面向资源检索场景的工作流组件及其请求、响应 DTO。 |
| [`tool`](./component/tool.README.md) | 提供工具调用型工作流组件、执行辅助类型与工具输入输出 DTO。 |

## Types

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [`AdvancedLoopComponent`](./component/AdvancedLoopComponent.md) | `interface` | 含循环体子图的高级循环组件接口。当前主要作为图可视化与高级循环构造的抽象入口。 |
| [`Branch`](./component/Branch.md) | `class` | 单条分支配置，描述条件与目标节点。 |
| [`BranchComponent`](./component/BranchComponent.md) | `class` | 条件路由组件，根据分支条件决定后续执行路径。 |
| [`ComponentAbility`](./component/ComponentAbility.md) | `enum` | 工作流组件执行能力枚举。 |
| [`ComponentConfig`](./component/ComponentConfig.md) | `class` | 公共工作流组件配置壳类型。 |
| [`ComponentState`](./component/ComponentState.md) | `class` | 公共工作流组件运行时状态壳类型。 |
| [`End`](./component/End.md) | `class` | 工作流结束组件，可选地执行响应模板渲染。 |
| [`EndConfig`](./component/EndConfig.md) | `class` | `End` 组件配置类型。 |
| [`IOConfig`](./component/IOConfig.md) | `class` | 工作流组件输入输出配置占位类型。 |
| [`IntentDetectionComponent`](./component/IntentDetectionComponent.md) | `class` | 意图识别组件，基于识别结果进行分支路由，继承自 `BranchComponent`。 |
| [`LoopComponent`](./component/LoopComponent.md) | `interface` | 包含可重复子图的循环组件接口。当前主要用于循环图抽象与可视化适配。 |
| [`NodeConfig`](./component/NodeConfig.md) | `class` | 工作流节点配置占位类型。 |
| [`Start`](./component/Start.md) | `class` | 工作流起点组件，按原样传递输入。 |
| [`SubWorkflowComponent`](./component/SubWorkflowComponent.md) | `interface` | 包装内部工作流图的子工作流组件接口。 |
| [`SubWorkflowComponentImpl`](./component/SubWorkflowComponentImpl.md) | `class` | 子工作流组件实现，负责包装内部工作流并委派执行。 |
| [`TemplateBatchProcessor`](./component/TemplateBatchProcessor.md) | `class` | `End` 组件使用的批量模板渲染器，在输入齐备后统一完成渲染。 |
| [`TemplateProcessor`](./component/TemplateProcessor.md) | `class` | `End` 组件使用的模板流式/同步渲染处理器，负责模板分段、变量位置与输出管理。 |
| [`TemplateUtils`](./component/TemplateUtils.md) | `class` | 模板拆分与渲染辅助工具。 |
| [`WorkflowComponentMetadata`](./component/WorkflowComponentMetadata.md) | `class` | 公共工作流组件元数据模型。 |

## 说明

- 当前包页汇总了 `component` 子树下已文档化的子包入口。
- 当前页面还链接了本包中 19 个直属公开类型页面。
- 代表性的工作流运行时与主要组件流程可参考 `WorkflowTest.java`。
