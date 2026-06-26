# com.openjiuwen.core.workflow.component.llm.IntentDetectionExecutable

## class IntentDetectionExecutable

```java
public class IntentDetectionExecutable extends ComponentExecutable
```

意图识别组件的可执行体。

该类型负责初始化模型、组装分类提示词、按需拼接历史消息，并在执行后把 LLM 输出解析为分类编号、分类名称和原因说明；如果配置了 `BranchRouter`，还会在调用前同步当前 `NodeSessionApi`。

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
