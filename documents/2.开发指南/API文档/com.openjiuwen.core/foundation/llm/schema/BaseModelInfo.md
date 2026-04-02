# com.openjiuwen.core.foundation.llm.schema.BaseModelInfo

## class BaseModelInfo

```java
public class BaseModelInfo
```

Base model information — a simplified configuration used by higher-level components.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `GREATER_THAN_ZERO_MESSAGE` | `String` | Stored `GREATER_THAN_ZERO_MESSAGE` value. |
| `apiKey` | `String` | Stored `apiKey` value. |
| `apiBase` | `String` | Stored `apiBase` value. |
| `modelName` | `String` | Stored `modelName` value. |
| `temperature` | `Double` | Stored `temperature` value. |
| `topP` | `Double` | Stored `topP` value. |
| `streaming` | `boolean` | Stored `streaming` value. |
| `timeout` | `int` | Stored `timeout` value. |
| `extraFields` | `Map<String, Object>` | Stored `extraFields` value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public BaseModelInfo(String apiKey, String apiBase, String modelName, Double temperature, Double topP, Boolean streaming, Integer timeout, Map<String, Object> extraFields)` | Create a new `BaseModelInfo` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Map<String, Object> getExtraFields()` | Return the `extraFields` value. |
| `public void setExtraField(String key, Object value)` | Set the `extraField` value. |
| `public void setTimeout(int timeout)` | Set the `timeout` value. |
| `public void setExtraFields(Map<String, Object> extraFields)` | Set the `extraFields` value. |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
