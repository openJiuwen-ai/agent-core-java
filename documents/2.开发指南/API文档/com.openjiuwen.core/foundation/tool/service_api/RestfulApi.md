# com.openjiuwen.core.foundation.tool.service_api.RestfulApi

## class RestfulApi

```java
public class RestfulApi extends Tool
```

RESTful API tool that executes HTTP requests. Uses JDK `HttpClient` instead of aiohttp.

## Notes

- GET calls place body-mapped inputs onto the query string, while non-GET calls serialize body parameters as JSON.
- Responses are normalized to a map containing `code`, `data`, `url`, `headers`, `reason`, and `message`, and they are parsed through `ParserRegistry`.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `url` | `String` | `-` | - |
| `method` | `String` | `-` | - |
| `timeout` | `double` | `-` | - |
| `maxResponseByteSize` | `int` | `-` | - |
| `apiParamMapper` | `ApiParamMapper` | `-` | - |

## Constructors

| Signature | Description |
| --- | --- |
| `public RestfulApi(RestfulApiCard card)` | Construct a new RestfulApi tool. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | - |

## Related Tests

- `RestfulApiTest`
