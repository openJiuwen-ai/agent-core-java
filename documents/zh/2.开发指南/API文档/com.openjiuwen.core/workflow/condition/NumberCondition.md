# com.openjiuwen.core.workflow.condition.NumberCondition

## 类 NumberCondition

```java
public class NumberCondition extends Condition
```

`NumberCondition` 是基于次数上限的循环条件，上限值从输入中读取。

## 说明

- 判断条件为当前 `INDEX < limit`。
- `WorkflowTest` 的循环场景验证了该类与工作流循环执行器的协作行为。
