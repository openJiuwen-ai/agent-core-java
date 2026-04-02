# com.openjiuwen.core.workflow.component.llm.OutputFormatter

## class OutputFormatter

```java
public final class OutputFormatter
```

LLM 响应格式化工具。

它会根据 `responseFormat.type` 选择文本、Markdown 或 JSON 格式化路径，并结合 `outputConfig` 对输出字段进行筛选、校验与重组。

## Methods

| Signature | Description |
| --- | --- |
| `public static Map<String, Object> formatResponse(String responseContent, Map<String, Object> responseFormat, Map<String, Object> outputsConfig)` | Format the LLM response into a structured output. |
