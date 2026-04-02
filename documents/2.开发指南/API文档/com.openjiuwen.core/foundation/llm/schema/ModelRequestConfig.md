# com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig

## class ModelRequestConfig

```java
public class ModelRequestConfig
```

Model request configuration (per-request parameters).

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `modelName` | `String` | Stored `modelName` value. |
| `temperature` | `Double` | Stored `temperature` value. |
| `topP` | `Double` | Stored `topP` value. |
| `maxTokens` | `Integer` | Stored `maxTokens` value. |
| `stop` | `String` | Stored `stop` value. |
| `user` | `String` | Stored `user` value. |
| `seed` | `Integer` | Stored `seed` value. |
| `extraFields` | `Map<String, Object>` | Extra fields that are not part of the standard config. |

## Methods

| Signature | Description |
| --- | --- |
| `public Map<String, Object> getExtraFields()` | Return the `extraFields` value. |
| `public void setExtraField(String key, Object value)` | Set the `extraField` value. |

## Notes

- Lombok annotations generate the standard accessors, equality helpers, and/or builder methods referenced by this type.
