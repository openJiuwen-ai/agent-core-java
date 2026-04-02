# com.openjiuwen.core.workflow.component.llm.LLMExecutableState

## class LLMExecutableState

```java
public class LLMExecutableState
```

State maintained by LLMExecutable for caching stream results.

## Methods

| Signature | Description |
| --- | --- |
| `public void accumulateContent(String content)` | Accumulate stream content chunks. |
| `public Map<String, Object> buildFinalResult(Map<String, Object> responseFormat, Map<String, Object> outputConfig)` | Build final result from accumulated content. |
| `public void clear()` | Clear state. |
| `public Map<String, Object> getFinalResult()` | Return the final result. |
| `public void setFinalResult(Map<String, Object> finalResult)` | Update the final result. |
