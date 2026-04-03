# loop

`com.openjiuwen.core.workflow.component.loop` 提供新版循环组件、循环体容器、输入模型与后置处理辅助类型。

## 子包

| 子包 | 说明 |
| --- | --- |
| [callback](./loop/callback.README.md) | 循环阶段回调与结果聚合类型。 |

## 类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [AdvancedLoopComponentImpl](./loop/AdvancedLoopComponentImpl.md) | `class` | 高级循环组件实现，负责条件判断、循环路由与循环体调度。 |
| [EmptyExecutable](./loop/EmptyExecutable.md) | `class` | 空执行节点，用作循环图中的条件占位和中转节点。 |
| [LoopBreakComponent](./loop/LoopBreakComponent.md) | `class` | 循环中断组件，在执行时通知 `LoopController` 结束当前循环。 |
| [LoopComponentImpl](./loop/LoopComponentImpl.md) | `class` | 循环组件实现，根据 `LoopInput` 组装条件、回调和底层 `AdvancedLoopComponentImpl`。 |
| [LoopController](./loop/LoopController.md) | `interface` | 循环控制接口，定义查询和中断循环的能力。 |
| [LoopGroup](./loop/LoopGroup.md) | `class` | 循环体容器，负责维护循环内部节点、边和 break 组件集合。 |
| [LoopInput](./loop/LoopInput.md) | `class` | 循环组件输入模型，描述循环类型、次数、数组源和中间变量配置。 |
| [LoopSetVariableComponent](./loop/LoopSetVariableComponent.md) | `class` | 循环变量写回组件，根据映射规则把值写回父会话。 |
| [LoopType](./loop/LoopType.md) | `enum` | 循环条件类型枚举，定义数组、次数和表达式等循环模式。 |
| [PostLoopBody](./loop/PostLoopBody.md) | `class` | 循环体后的收尾节点，用于记录已完成的轮次索引。 |

## 说明

- 当前包收录 10 个类型页面。
- 当前包同步暴露 1 个子包入口。
