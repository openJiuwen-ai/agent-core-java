# com.openjiuwen.core.foundation.tool.service_api.ApiParamLocation

## enum ApiParamLocation

```java
public enum ApiParamLocation
```

API parameter locations based on OpenAPI specification.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `value` | `String` | `-` | HTTP header parameters. */ |

## Enum Values

| Value | Description |
| --- | --- |
| `QUERY` | - |
| `PATH` | - |
| `BODY` | - |
| `HEADER` | - |

## Constructors

| Signature | Description |
| --- | --- |
| `ApiParamLocation(String value)` | - |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | - |
| `public static ApiParamLocation fromString(String text)` | Parse a location string (case-insensitive). |

## Related Tests

- `ApiParamMapperTest`
