# com.openjiuwen.core.workflow.component.llm.LLMPromptFormatter

## class LLMPromptFormatter

```java
public final class LLMPromptFormatter
```

Formats prompts for LLM components, injecting format instructions (markdown / JSON schema) into the last user message.

## Methods

| Signature | Description |
| --- | --- |
| `public static List<BaseMessage> formatPrompt(List<BaseMessage> history, Map<String, Object> responseFormat, Map<String, Object> outputConfig)` | Format prompt history with response format instructions. |
