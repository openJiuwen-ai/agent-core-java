# com.openjiuwen.core.controller.schema.IntentType

## enum IntentType

```java
public enum IntentType
```

`IntentType` 描述控制器目前支持的意图分类。它们被 `IntentToolkits` 和后续事件处理流程共同使用，用来区分任务创建、暂停、恢复、补充等不同操作。

## 枚举值

| 枚举值 | 字符串值 | 说明 |
|---|---|---|
| `CREATE_TASK` | "create_task" | 创建新任务。 |
| `PAUSE_TASK` | "pause_task" | 暂停正在执行的任务。 |
| `RESUME_TASK` | "resume_task" | 恢复已暂停的任务。 |
| `CONTINUE_TASK` | "continue_task" | 基于既有任务继续派生后续任务。 |
| `SUPPLEMENT_TASK` | "supplement_task" | 为既有任务补充额外信息。 |
| `CANCEL_TASK` | "cancel_task" | 取消当前任务。 |
| `MODIFY_TASK` | "modify_task" | 修改任务描述或执行要求。 |
| `SWITCH_TASK` | "switch_task" | 中断当前任务并切换到另一项任务。 |
| `UNKNOWN_TASK` | "unknown_task" | 当前意图不明确，需要进一步澄清。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getValue()` | `String` | 返回当前枚举常量绑定的字符串值。 |
| `fromValue(String value)` | `IntentType` | 按字符串值解析枚举；未知值时抛出 `IllegalArgumentException`。 |
| `toString()` | `String` | 直接返回字符串值。 |
