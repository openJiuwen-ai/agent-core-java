# com.openjiuwen.core.workflow.component.llm.QuestionerStartState

## class QuestionerStartState

```java
public class QuestionerStartState extends QuestionerState
```

Questioner 状态机的起始态。

该状态用于表示一次新的字段收集流程刚开始，后续可根据抽取结果进入交互态或直接结束。

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerStartState()` | Create a new `QuestionerStartState` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerStartState fromState(QuestionerState state)` | Create from an existing `QuestionerState`. |
| `public QuestionerState handleEvent(QuestionerEvent event)` | Execute `handleEvent`. |
