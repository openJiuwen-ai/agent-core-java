# com.openjiuwen.core.workflow.component.llm.QuestionerEndState

## class QuestionerEndState

```java
public class QuestionerEndState extends QuestionerState
```

Questioner END state. fixed to `ExecutionStatus#END`. Can loop back to START via START_EVENT.

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerEndState()` | Create a new `QuestionerEndState` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerEndState fromState(QuestionerState state)` | Create from an existing `QuestionerState`. |
| `public QuestionerState handleEvent(QuestionerEvent event)` | Execute `handleEvent`. |
