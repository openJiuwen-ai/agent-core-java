# com.openjiuwen.core.workflow.condition.FuncCondition

## 类 FuncCondition

```java
public class FuncCondition extends Condition
```

`FuncCondition` 使用 `BooleanSupplier` 包装函数式条件。

## 说明

- `doInvoke(...)` 直接调用传入的布尔函数。
- 适合表达简单动态条件或外部闭包条件。
