# com.openjiuwen.core.workflow.condition.ArrayCondition

## 类 ArrayCondition

```java
public class ArrayCondition extends Condition
```

`ArrayCondition` 用于数组型循环条件：从输入 schema 解析数组，并按当前 `INDEX` 提取本轮值。

## 说明

- 任一值不是 `List` 或索引越界时返回 `false`。
- 命中时会把当前轮提取出的值写回 session。
