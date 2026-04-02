# components

`com.openjiuwen.core.workflow.components` 保留旧版工作流组件包布局，供历史文档与旧集成兼容使用。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`flow`](./components/flow.README.md) | 收录旧版流程组件，如开始、结束、分支与子工作流包装器。 |
| [`llm`](./components/llm.README.md) | 收录旧版工作流包布局中的 LLM 组件模型与配置类型。 |
| [`tool`](./components/tool.README.md) | 收录旧版工作流包布局中的工具组件模型。 |

## 说明

- 当前包页汇总了 `components` 子树下已文档化的子包入口。
- 代表性的工作流运行时与主要组件流程可参考 `WorkflowTest.java`。
