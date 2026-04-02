# com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard

## class RestfulApiCard

```java
public class RestfulApiCard extends ToolCard
```

HTTP tool metadata that captures the target URL, HTTP method, default headers/query/path values, timeout, and response-size limit.

## Notes

- This type relies on Lombok-generated accessors and/or builders; the tables below document the explicit fields declared in source.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `url` | `String` | `-` | Restful API URL, e.g. /api/v1/users. */ |
| `method` | `String` | `"POST"` | HTTP method (POST or GET). */ |
| `timeout` | `double` | `60.0` | Request timeout in seconds. */ |
| `maxResponseByteSize` | `int` | `10 * 1024 * 1024` | Maximum response size in bytes (default 10 MB). */ |

## Methods

| Signature | Description |
| --- | --- |
| `public static final Set<String> SUPPORTED_METHODS = Set.of("POST", "GET")` | Supported HTTP methods. */ |

## Related Tests

- `RestfulApiTest`
