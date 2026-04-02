# com.openjiuwen.core.workflow.component.llm.QuestionerInteractState

## class QuestionerInteractState

```java
public class QuestionerInteractState extends QuestionerState
```

Questioner USER_INTERACT state. fixed to `ExecutionStatus#USER_INTERACT`. Can only transition to END.

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerInteractState()` | Create a new `QuestionerInteractState` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerInteractState fromState(QuestionerState state)` | Create from an existing `QuestionerState`. |
| `public QuestionerState handleEvent(QuestionerEvent event)` | Execute `handleEvent`. |
