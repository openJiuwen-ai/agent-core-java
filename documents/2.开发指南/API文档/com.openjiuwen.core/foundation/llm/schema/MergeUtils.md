# com.openjiuwen.core.foundation.llm.schema.MergeUtils

## class MergeUtils

```java
public final class MergeUtils
```

Utility class for merging streaming message chunks and parser content.

## Nested Types

| Declaration | Description |
| --- | --- |
| `public interface Mergeable<T>` | Interface for objects that support merging (Java equivalent of Python's __add__). |

## Methods

| Signature | Description |
| --- | --- |
| `public static Object mergeParserContent(Object left, Object right)` | Intelligently merge parser_content fields. |
| `public static Map<String, Object> mergeMaps(Map<String, Object> left, Map<String, Object> right)` | Recursively merge two maps. |
| `public static <T> T mergeObjects(T left, T right)` | Merge two same-type POJO instances field-by-field using JavaBeans introspection. |

## Notes

- `MergeUtilsTest` covers recursive merge behavior for strings, lists, maps, and nested objects.
