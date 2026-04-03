# com.openjiuwen.core.workflow.component.llm.QuestionerDirectReplyHandler

## class QuestionerDirectReplyHandler

```java
public class QuestionerDirectReplyHandler
```

`reply_directly` 模式下的 Questioner 处理器。

它会根据当前状态执行字段抽取、默认值补齐、继续追问判断与输出整理，是多轮追问流程的核心执行逻辑。

## Methods

| Signature | Description |
| --- | --- |
| `public QuestionerDirectReplyHandler config(QuestionerConfig config)` | Execute `config`. |
| `public QuestionerDirectReplyHandler model(Model model)` | Execute `model`. |
| `public QuestionerDirectReplyHandler state(QuestionerState state)` | Execute `state`. |
| `public QuestionerState getState()` | Return the state. |
| `public QuestionerDirectReplyHandler prompt(PromptTemplate prompt)` | Execute `prompt`. |
| `public Map<String, Object> handle(Object inputs, NodeSessionApi session, ModelContext context)` | Execute the handler based on current state. |
