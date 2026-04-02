# com.openjiuwen.core.common.schema.ParamType

## enum ParamType

```java
public enum ParamType
```

`ParamType` enumerates the supported parameter kinds used by `Param`.

## Enum Values

| Value | Description |
| --- | --- |
| `STRING` | String value represented as `"string"`. |
| `BOOLEAN` | Boolean value represented as `"boolean"`. |
| `INTEGER` | Integer value represented as `"integer"`. |
| `NUMBER` | Floating-point numeric value represented as `"number"`. |
| `ARRAY` | Array value represented as `"array"`. |
| `OBJECT` | Object value represented as `"object"`. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Return the lowercase schema token for the enum constant. |
| `public static ParamType fromValue(String value)` | Resolve a case-insensitive string token to the matching enum constant. |

## Notes

- `fromValue` throws `IllegalArgumentException` when the token does not map to any supported type.
