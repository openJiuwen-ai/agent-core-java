# com.openjiuwen.core.workflow.component.llm.LLMComponent

## class LLMComponent

```java
public class LLMComponent implements ComponentComposable
```

Workflow component that wraps an LLM model for invocation and streaming.

## Constructors

| Signature | Description |
| --- | --- |
| `public LLMComponent(LLMCompConfig componentConfig)` | Create a new `LLMComponent` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public LLMExecutable getExecutable()` | Return the executable. |
| `public Executable<?, ?> toExecutable()` | Execute `toExecutable`. |
