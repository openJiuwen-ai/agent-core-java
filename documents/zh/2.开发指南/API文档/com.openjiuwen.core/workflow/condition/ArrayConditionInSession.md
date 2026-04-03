# com.openjiuwen.core.workflow.condition.ArrayConditionInSession

## 类 ArrayConditionInSession

```java
public class ArrayConditionInSession extends Condition
```

`ArrayConditionInSession` 直接使用 session 中已有数组执行循环判断。

## 说明

- 构造时会校验所有值必须为非空 `List`。
- 命中时同样会更新 session 中的当前轮输出。
