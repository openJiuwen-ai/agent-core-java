# com.openjiuwen.core.workflow.component.llm.LLMPromptFormatter

## class LLMPromptFormatter

```java
public final class LLMPromptFormatter
```

LLM 提示词格式化工具。

该工具会定位最后一条 `user` 消息，并在 `markdown` 或 `json` 模式下把格式约束和 JSON Schema 注入到提示词文本中；`text` 模式下则不改写消息内容。

## Methods

| Signature | Description |
| --- | --- |
| `public static List<BaseMessage> formatPrompt(List<BaseMessage> history, Map<String, Object> responseFormat, Map<String, Object> outputConfig)` | Format prompt history with response format instructions. |
