# com.openjiuwen.core.foundation.llm.schema.ProviderType

## enum ProviderType

```java
public enum ProviderType
```

Model client provider type enumeration.

## Enum Values

| Value | Description |
| --- | --- |
| `OpenAI` | Enum constant declared on `ProviderType`. |
| `OpenRouter` | Enum constant declared on `ProviderType`. |
| `SiliconFlow` | Enum constant declared on `ProviderType`. |
| `DashScope` | Enum constant declared on `ProviderType`. |

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `value` | `String` | Stored `value` value. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Return the `value` value. |
| `public static ProviderType fromValue(String value)` | Look up a provider type by its string value. |

## Notes

- `ModelClientConfigTest` verifies case-insensitive provider lookup and the invalid-provider failure path.
