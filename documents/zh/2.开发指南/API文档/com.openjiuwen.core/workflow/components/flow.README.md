# flow

`com.openjiuwen.core.workflow.components.flow` 提供旧版兼容的 flow 组件包装类型，包括开始、结束、分支、子工作流与 legacy loop。

## 子包

| 子包 | 说明 |
| --- | --- |
| [loop](./flow/loop.README.md) | 旧版兼容的 loop 组件与循环体容器。 |

## 类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [BranchComponent](./flow/BranchComponent.md) | `class` | 旧版兼容分支组件别名，复用当前分支实现。 |
| [EndComponent](./flow/EndComponent.md) | `class` | 旧版兼容结束节点别名，表示工作流出口。 |
| [StartComponent](./flow/StartComponent.md) | `class` | 旧版兼容开始节点别名，表示工作流入口。 |
| [SubWorkflowComponent](./flow/SubWorkflowComponent.md) | `class` | 旧版兼容子工作流组件包装类型，复用 `SubWorkflowComponentImpl`。 |

## 说明

- 当前包收录 4 个类型页面。
- 当前包同步暴露 1 个子包入口。
