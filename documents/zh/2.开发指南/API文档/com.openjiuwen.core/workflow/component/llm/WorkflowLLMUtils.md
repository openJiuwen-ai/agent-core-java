# com.openjiuwen.core.workflow.component.llm.WorkflowLLMUtils

## class WorkflowLLMUtils

```java
public final class WorkflowLLMUtils
```

工作流 LLM 通用工具类。

当前仅提供 `extractContent(Object response)`，优先通过反射调用 `getContent()` 提取响应内容，失败时退回对象的 `toString()`。

## Methods

| Signature | Description |
| --- | --- |
| `public static String extractContent(Object response)` | Extract content string from an LLM response object. |
