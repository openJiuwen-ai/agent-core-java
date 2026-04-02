# com.openjiuwen.core.workflow.component.llm.LLMExecutable

## class LLMExecutable

```java
public class LLMExecutable extends ComponentExecutable
```

LLM 工作流组件的可执行体。

它负责校验配置、初始化 `Model`、构造 system/user prompt、插入历史消息，并分别支持同步 `invoke(...)` 与流式 `stream(...)`；当启用 `cacheStream` 时，还能在流结束后汇总最终输出。

## Constructors

| Signature | Description |
| --- | --- |
| `public LLMExecutable(LLMCompConfig componentConfig)` | Create a new `LLMExecutable` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public LLMCompConfig getConfig()` | Return the config. |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | Invoke the component or workflow. |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | Stream the component or workflow output. |
| `public Map<String, Object> getStreamOutput()` | Get the final output from cached stream content. |
