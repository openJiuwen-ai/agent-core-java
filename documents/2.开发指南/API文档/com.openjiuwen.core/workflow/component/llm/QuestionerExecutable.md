# com.openjiuwen.core.workflow.component.llm.QuestionerExecutable

## class QuestionerExecutable

```java
public class QuestionerExecutable extends ComponentExecutable
```

Executable for the Questioner workflow component. Manages state machine lifecycle, LLM initialization, and delegates to `QuestionerDirectReplyHandler` for actual extraction.

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerExecutable(QuestionerConfig config)` | Create a new `QuestionerExecutable` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public QuestionerExecutable state(QuestionerState state)` | Execute `state`. |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | Invoke the component or workflow. |
