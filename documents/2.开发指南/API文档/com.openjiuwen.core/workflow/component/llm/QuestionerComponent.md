# com.openjiuwen.core.workflow.component.llm.QuestionerComponent

## class QuestionerComponent

```java
public class QuestionerComponent implements ComponentComposable
```

Questioner 工作流组件封装。

该类本身不执行业务逻辑，只负责基于 `QuestionerConfig` 生成 `QuestionerExecutable`，并在构建执行体时附带新的初始状态对象。

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerComponent(QuestionerConfig config)` | Create a new `QuestionerComponent` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Executable<?, ?> toExecutable()` | Execute `toExecutable`. |
