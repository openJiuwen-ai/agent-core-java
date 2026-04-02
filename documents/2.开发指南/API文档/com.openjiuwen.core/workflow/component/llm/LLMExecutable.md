# com.openjiuwen.core.workflow.component.llm.LLMExecutable

## class LLMExecutable

```java
public class LLMExecutable extends ComponentExecutable
```

Executable for LLM workflow component, handling model invocation and streaming.

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
