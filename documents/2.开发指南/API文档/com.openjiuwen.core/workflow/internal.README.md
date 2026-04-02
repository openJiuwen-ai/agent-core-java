# internal

`com.openjiuwen.core.workflow.internal` 放置工作流包内部使用的兼容桥接工具。当前只有 `LegacyWorkflowComponentSupport` 一个公开类型，用于把旧式 POJO 节点适配为新的 `ComponentComposable` 组件模型。

## 类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [`LegacyWorkflowComponentSupport`](./internal/LegacyWorkflowComponentSupport.md) | `class` | 旧组件兼容桥，用反射方式适配 `invoke`、`stream`、`collect`、`transform`。 |

## 说明

- 该包属于运行时兼容层，不是新的工作流组件开发入口。
- `Workflow` 中接收 `Object component` 的多组兼容重载最终都会依赖这里的适配逻辑。
