# com.openjiuwen.core.controller.schema.EventType

## enum EventType

```java
public enum EventType
```

`EventType` 描述控制器事件流中使用的事件类别。每个枚举常量都绑定一个对外传输使用的字符串值。

## 枚举值

| 枚举值 | 字符串值 | 说明 |
|---|---|---|
| `INPUT` | "input" | 用户输入事件。 |
| `TASK_INTERACTION` | "task_interaction" | 任务执行过程中需要用户继续交互。 |
| `TASK_COMPLETION` | "task_completion" | 任务正常完成。 |
| `TASK_FAILED` | "task_failed" | 任务执行失败。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getValue()` | `String` | 返回当前枚举常量绑定的字符串值。 |
| `fromValue(String value)` | `EventType` | 按字符串值查找对应枚举；找不到时抛出 `IllegalArgumentException`。 |
| `toString()` | `String` | 直接返回字符串值。 |
