# tool

`com.openjiuwen.core.workflow.component.tool` 提供 Tool 调用型工作流组件、执行器与输入输出/配置模型。

## 类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [ToolComponent](./tool/ToolComponent.md) | `class` | Tool 组件封装类型，负责绑定 `Tool` 并生成 `ToolExecutable`。 |
| [ToolComponentConfig](./tool/ToolComponentConfig.md) | `class` | Tool 组件配置模型，声明要绑定的 `toolId`。 |
| [ToolComponentInput](./tool/ToolComponentInput.md) | `class` | Tool 组件输入模型，保存传给工具的动态键值对。 |
| [ToolComponentOutput](./tool/ToolComponentOutput.md) | `class` | Tool 组件输出模型，封装错误码、错误消息与工具返回数据。 |
| [ToolExecutable](./tool/ToolExecutable.md) | `class` | Tool 组件执行器，负责校验输入、调用工具并包装输出。 |

## 说明

- 当前包收录 5 个类型页面。
