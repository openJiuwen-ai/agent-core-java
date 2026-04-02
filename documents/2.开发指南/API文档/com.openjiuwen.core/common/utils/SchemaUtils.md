# com.openjiuwen.core.common.utils.SchemaUtils

## class SchemaUtils

```java
public final class SchemaUtils
```

`SchemaUtils` fills defaults, validates `Map<String, Object>` payloads against a JSON-Schema-like structure, reflects simple schemas from Java classes, and removes nested `null` values when requested.

## Constructors

| Signature | Description |
| --- | --- |
| `private SchemaUtils()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static Map<String, Object> formatWithSchema(Map<String, Object> data, Map<String, Object> schema)` | Format `data` with defaults from `schema` and then validate the result. |
| `public static Map<String, Object> formatWithSchema(Map<String, Object> data, Map<String, Object> schema, boolean skipValidate)` | Format `data` while optionally skipping the validation pass. |
| `public static Map<String, Object> formatWithSchema(Map<String, Object> data, Map<String, Object> schema, boolean skipNoneValue, boolean skipValidate)` | Optionally remove nested `null` values, apply schema defaults, and validate unless `skipValidate` is `true`. |
| `public static void validateWithSchema(Map<String, Object> data, Map<String, Object> schema)` | Check required fields plus basic type, string-length, numeric-range, and array-size constraints. |
| `public static Map<String, Object> getSchemaDict(Class<?> clazz)` | Build a simple object-schema map from the declared fields of `clazz`, or return `null` when `clazz` is `null`. |
| `public static Map<String, Object> removeNoneValues(Map<String, Object> data)` | Recursively strip `null` entries from nested maps and lists, returning `null` when every value is removed. |

## Notes

- `formatWithSchema` and `validateWithSchema` both throw `ValidationError` with status codes `SCHEMA_FORMAT_INVALID` or `SCHEMA_VALIDATE_INVALID` when processing fails.
- Mutable default values from the schema are deep-copied for `Map` and `List` defaults before they are inserted into the result.
- `SchemaUtilsTest` covers default population, validation failures, `getSchemaDict`, and list/map default handling.
