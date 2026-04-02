# com.openjiuwen.core.workflow.component.llm.ResponseFormatConfig

## class ResponseFormatConfig

```java
public class ResponseFormatConfig
```

LLM 响应格式配置模型。

该类型只接受 `text`、`markdown`、`json` 三种响应类型，并支持通过 `fromMap(...)` 从包含 `type` 键的映射中完成校验与构造。

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
