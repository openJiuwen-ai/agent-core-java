# com.openjiuwen.core.workflow.component.llm.QuestionerEvent

## enum QuestionerEvent

```java
public enum QuestionerEvent
```

Questioner 状态迁移事件枚举。

当前只定义启动、用户交互和结束三类事件，用于驱动 `QuestionerState` 在不同子状态之间切换。

## Enum Constants

| Value | Description |
| --- | --- |
| `START_EVENT` | 进入起始态。 |
| `END_EVENT` | 进入结束态。 |
| `USER_INTERACT_EVENT` | 进入用户交互态。 |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Return the value. |
