# condition

`com.openjiuwen.core.workflow.condition` 提供工作流分支与循环控制使用的条件实现，覆盖恒真条件、表达式条件、函数条件、数组迭代条件以及基于次数的循环条件。

## 类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [`AlwaysTrue`](./condition/AlwaysTrue.md) | `class` | 永远返回 `true` 的条件实现。 |
| [`ArrayCondition`](./condition/ArrayCondition.md) | `class` | 从输入 schema 解析数组，并按当前循环下标提取当前轮输入。 |
| [`ArrayConditionInSession`](./condition/ArrayConditionInSession.md) | `class` | 直接使用 session 中已有数组进行循环判断。 |
| [`Condition`](./condition/Condition.md) | `abstract class` | 条件体系的统一抽象基类。 |
| [`ExpressionCondition`](./condition/ExpressionCondition.md) | `class` | 支持 `${...}` 变量替换、逻辑运算、比较和函数调用的表达式条件。 |
| [`FuncCondition`](./condition/FuncCondition.md) | `class` | 使用 `BooleanSupplier` 包装的函数式条件。 |
| [`NumberCondition`](./condition/NumberCondition.md) | `class` | 从输入中读取上限值的次数型循环条件。 |
| [`NumberConditionInSession`](./condition/NumberConditionInSession.md) | `class` | 直接持有固定上限值的次数型循环条件。 |

## 关键行为

- 所有条件实现最终都通过 `Condition.evaluate(BaseSession)` 对外暴露布尔判断能力。
- `ArrayCondition` 与 `ArrayConditionInSession` 在命中当前轮时会把当前轮提取出的值写回 session。
- `ExpressionCondition` 支持 `&&`、`||`、`!`、`==`、`!=`、`<`、`<=`、`>`、`>=`、`in`、`not_in`，以及 `length()`、`is_empty()`、`is_not_empty()`。
- `WorkflowTest` 通过分支路由和循环场景验证了表达式条件、数字循环条件及其与工作流执行器的协作行为。
