# com.openjiuwen.core.workflow.component.llm.LLMExecutableState

## class LLMExecutableState

```java
public class LLMExecutableState
```

`LLMExecutable` 在流式调用时使用的缓存状态对象。

它会持续累积分片内容，并在需要时把累计文本重新格式化为最终输出结果；`clear()` 会同时清空缓冲区与暂存结果。

## Methods

| Signature | Description |
| --- | --- |
| `public void accumulateContent(String content)` | Accumulate stream content chunks. |
| `public Map<String, Object> buildFinalResult(Map<String, Object> responseFormat, Map<String, Object> outputConfig)` | Build final result from accumulated content. |
| `public void clear()` | Clear state. |
| `public Map<String, Object> getFinalResult()` | Return the final result. |
| `public void setFinalResult(Map<String, Object> finalResult)` | Update the final result. |
