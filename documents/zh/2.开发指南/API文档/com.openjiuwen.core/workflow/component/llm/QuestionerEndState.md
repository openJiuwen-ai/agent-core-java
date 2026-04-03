# com.openjiuwen.core.workflow.component.llm.QuestionerEndState

## class QuestionerEndState

```java
public class QuestionerEndState extends QuestionerState
```

Questioner 状态机的结束态。

该类型继承 `QuestionerState` 并把 `status` 固定为 `ExecutionStatus.END`；当收到 `START_EVENT` 时，可重新回到新的起始态实例。

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerEndState()` | Create a new `QuestionerEndState` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerEndState fromState(QuestionerState state)` | Create from an existing `QuestionerState`. |
| `public QuestionerState handleEvent(QuestionerEvent event)` | Execute `handleEvent`. |
