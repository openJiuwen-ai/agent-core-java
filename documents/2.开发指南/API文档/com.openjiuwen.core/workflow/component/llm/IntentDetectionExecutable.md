# com.openjiuwen.core.workflow.component.llm.IntentDetectionExecutable

## class IntentDetectionExecutable

```java
public class IntentDetectionExecutable extends ComponentExecutable
```

Executable for intent detection that invokes an LLM to classify user input and routes to the appropriate branch.

## Constructors

| Signature | Description |
| --- | --- |
| `public IntentDetectionExecutable(IntentDetectionCompConfig componentConfig)` | Create a new `IntentDetectionExecutable` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | Invoke the component or workflow. |
| `public IntentDetectionExecutable setRouter(BranchRouter router)` | Set the branch router. |
| `public boolean postCommit()` | Execute `postCommit`. |
