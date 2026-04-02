# com.openjiuwen.core.workflow.component.llm.QuestionerExecutable

## class QuestionerExecutable

```java
public class QuestionerExecutable extends ComponentExecutable
```

Questioner 工作流组件的可执行体。

它会优先从 `NodeSessionApi` 恢复节点状态，再初始化模型并把字段抽取工作委托给 `QuestionerDirectReplyHandler`；若流程仍在交互中，则把状态写回 session 并触发 `session.interact(...)`。

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerExecutable(QuestionerConfig config)` | Create a new `QuestionerExecutable` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public QuestionerExecutable state(QuestionerState state)` | Execute `state`. |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | Invoke the component or workflow. |
