# com.openjiuwen.core.common.utils.DictUtils

## class DictUtils

```java
public final class DictUtils
```

`DictUtils` provides nested `Map` / `List` transformation helpers for dotted-path construction, flattening, leaf extraction, and path-based rebuilds.

## Constructors

| Signature | Description |
| --- | --- |
| `private DictUtils()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static Object createNestedMap(String path, Object value, String separator)` | Build nested `LinkedHashMap` layers from `path`, returning `value` directly when `path` is `null` or empty. |
| `public static Object createNestedMap(String path, Object value)` | Convenience overload that uses `.` as the path separator. |
| `public static Map<String, Object> flattenMap(Map<String, Object> data)` | Convert a nested structure into dotted-path keys by combining `extractLeafNodes` with `formatPath`. |
| `public static List<Map.Entry<List<String>, Object>> extractLeafNodes(Object data, List<String> currentPath)` | Walk nested maps and lists recursively and emit one `(path, value)` entry for each leaf node. |
| `public static String formatPath(List<String> path)` | Join dictionary keys with dots while appending list-index segments such as `[0]` directly. |
| `public static Map<String, Object> rebuildMapFromPaths(Iterable<Map.Entry<List<String>, Object>> pathValuePairs)` | Rebuild nested maps from path/value pairs when every path segment represents a map key. |
| `public static Map<String, Object> rebuildDict(Iterable<Map.Entry<List<String>, Object>> pathValuePairs)` | Rebuild a nested structure that may contain both maps and list indices formatted as `[index]`. |

## Notes

- `DictUtilsTest` verifies complex leaf extraction, reconstruction equivalence, list-index handling, custom separators, flattening, and path formatting edge cases.
- `rebuildDict` creates intermediate `ArrayList` or `LinkedHashMap` containers based on the next path segment.
