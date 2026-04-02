# com.openjiuwen.core.workflow.component.llm.QuestionerStartState

## class QuestionerStartState

```java
public class QuestionerStartState extends QuestionerState
```

Questioner START state. fixed to `ExecutionStatus#START`. Transitions: can move to INTERACT or END.

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerStartState()` | Create a new `QuestionerStartState` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerStartState fromState(QuestionerState state)` | Create from an existing `QuestionerState`. |
| `public QuestionerState handleEvent(QuestionerEvent event)` | Execute `handleEvent`. |
