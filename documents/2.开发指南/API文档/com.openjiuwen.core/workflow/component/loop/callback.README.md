# callback

`com.openjiuwen.core.workflow.component.loop.callback` 定义循环阶段回调类型，用于聚合输出和记录中间变量。

## 类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [IntermediateLoopVarCallback](./callback/IntermediateLoopVarCallback.md) | `class` | 循环回调实现，将每轮中间变量写入指定根路径。 |
| [LoopCallback](./callback/LoopCallback.md) | `class` | 循环回调抽象基类，定义首轮、每轮开始/结束与出循环阶段事件。 |
| [OutputCallback](./callback/OutputCallback.md) | `class` | 循环回调实现，按输出 schema 聚合每轮结果并写回会话。 |

## 说明

- 当前包收录 3 个类型页面。
