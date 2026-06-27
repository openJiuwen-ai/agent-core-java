# com.openjiuwen.core.workflow.component.loop.LoopType

## 枚举 LoopType

```java
public enum LoopType
```

循环条件类型枚举，定义数组、次数和表达式等循环模式。

## 枚举值

| 值 | 说明 |
| --- | --- |
| `ARRAY` | 按数组或集合元素驱动循环。 |
| `NUMBER` | 按固定次数执行循环。 |
| `ALWAYS_TRUE` | 在条件恒真时持续循环。 |
| `EXPRESSION` | 按表达式求值结果决定是否继续循环。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回`value` 字段。 |
| `public static LoopType fromValue(String value)` | 根据字符串值解析枚举项。 |
