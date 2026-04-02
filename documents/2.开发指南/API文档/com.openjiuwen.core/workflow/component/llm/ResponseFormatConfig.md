# com.openjiuwen.core.workflow.component.llm.ResponseFormatConfig

## class ResponseFormatConfig

```java
public class ResponseFormatConfig
```

Configuration model for LLM response format. Validates that the response type is one of: text, markdown, json.

## Fields

| Signature | Description |
| --- | --- |
| `private final String responseType` | Response type. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ResponseFormatConfig(String responseType)` | Create a new `ResponseFormatConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getResponseType()` | Return the response type. |
| `public static ResponseFormatConfig fromMap(Map<String, Object> map)` | Validate and create from a map (looks for "type" key). |
