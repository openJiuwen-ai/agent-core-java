# com.openjiuwen.core.workflow.condition.ExpressionCondition

## 类 ExpressionCondition

```java
public class ExpressionCondition extends Condition
```

`ExpressionCondition` 支持 `${...}` 变量替换、逻辑运算、比较运算和少量函数调用，是最常用的表达式条件实现。

## 说明

- 支持 `&&`、`||`、`!`、`==`、`!=`、`<`、`<=`、`>`、`>=`、`in`、`not_in`。
- 支持 `length()`、`is_empty()`、`is_not_empty()`。
- 变量从 `WorkflowStateCollection` 的全局状态中解析。
- `WorkflowTest` 的分支路由场景覆盖了该类的核心行为。
