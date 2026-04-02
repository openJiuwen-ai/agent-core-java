# com.openjiuwen.core.workflow.component.llm.QuestionerDirectReplyHandler

## class QuestionerDirectReplyHandler

```java
public class QuestionerDirectReplyHandler
```

Handles "reply directly" questioner flow: field extraction via LLM, state machine transitions, and follow-up question generation.

## Methods

| Signature | Description |
| --- | --- |
| `public QuestionerDirectReplyHandler config(QuestionerConfig config)` | Execute `config`. |
| `public QuestionerDirectReplyHandler model(Model model)` | Execute `model`. |
| `public QuestionerDirectReplyHandler state(QuestionerState state)` | Execute `state`. |
| `public QuestionerState getState()` | Return the state. |
| `public QuestionerDirectReplyHandler prompt(PromptTemplate prompt)` | Execute `prompt`. |
| `public Map<String, Object> handle(Object inputs, NodeSessionApi session, ModelContext context)` | Execute the handler based on current state. |
