# com.openjiuwen.core.workflow.condition.NumberConditionInSession

## 类 NumberConditionInSession

```java
public class NumberConditionInSession extends Condition
```

`NumberConditionInSession` 是固定上限值的次数型循环条件。

## 说明

- 构造时直接持有 `limit`。
- 执行时判断当前 `INDEX` 是否小于该固定值。
