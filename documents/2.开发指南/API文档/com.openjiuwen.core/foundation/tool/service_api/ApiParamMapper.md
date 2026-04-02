# com.openjiuwen.core.foundation.tool.service_api.ApiParamMapper

## class ApiParamMapper

```java
public class ApiParamMapper
```

Maps input parameters to their corresponding API locations (query, path, body, header). Handles parameter distribution based on schema definitions and provides default value merging for query, path, and header parameters.

## Notes

- When defaults are configured for query, header, or path locations, user-supplied inputs override those defaults during `map(...)`.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `schema` | `Map<String, Object>` | `-` | - |
| `defaults` | `Map<ApiParamLocation, Map<String, Object>>` | `-` | - |

## Constructors

| Signature | Description |
| --- | --- |
| `public ApiParamMapper(Map<String, Object> schema, Map<String, Object> defaultQueries, Map<String, Object> defaultHeaders, Map<String, Object> defaultPaths)` | Construct a new API parameter mapper. |

## Methods

| Signature | Description |
| --- | --- |
| `public Map<ApiParamLocation, Map<String, Object>> map(Map<String, Object> inputs, ApiParamLocation defaultLocation)` | Map input parameters to their respective API locations. |

## Related Tests

- `ApiParamMapperTest`
