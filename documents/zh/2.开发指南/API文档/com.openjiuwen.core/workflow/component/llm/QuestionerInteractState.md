# com.openjiuwen.core.workflow.component.llm.QuestionerInteractState

## class QuestionerInteractState

```java
public class QuestionerInteractState extends QuestionerState
```

Questioner 状态机的用户交互态。

该状态表示组件已经向用户提出追问并等待下一轮回复；此时状态机会保留已抽取字段和当前问题文本，直至收到结束事件。

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerInteractState()` | Create a new `QuestionerInteractState` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerInteractState fromState(QuestionerState state)` | Create from an existing `QuestionerState`. |
| `public QuestionerState handleEvent(QuestionerEvent event)` | Execute `handleEvent`. |
