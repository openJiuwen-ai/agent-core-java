# com.openjiuwen.core.common.utils.DictUtils

## class DictUtils

```java
public final class DictUtils
```

`DictUtils` 提供嵌套 `Map`/`List` 结构的构造、拍平、叶子提取、路径格式化与重建能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static Object createNestedMap(String path, Object value, String separator)` | 根据分隔路径创建嵌套 `Map`；当 `path` 为空时直接返回 `value`。 |
| `public static Object createNestedMap(String path, Object value)` | 使用默认分隔符 `.` 创建嵌套 `Map`。 |
| `public static Map<String, Object> flattenMap(Map<String, Object> data)` | 将嵌套结构拍平成点路径键的单层 `Map`。 |
| `public static List<Map.Entry<List<String>, Object>> extractLeafNodes(Object data, List<String> currentPath)` | 深度遍历嵌套 `Map`/`List`，提取所有叶子节点及其路径。 |
| `public static String formatPath(List<String> path)` | 将路径列表格式化为 `a.b[0].c` 形式的字符串。 |
| `public static Map<String, Object> rebuildMapFromPaths(Iterable<Map.Entry<List<String>, Object>> pathValuePairs)` | 依据路径值对重建仅包含 `Map` 的嵌套结构。 |
| `public static Map<String, Object> rebuildDict(Iterable<Map.Entry<List<String>, Object>> pathValuePairs)` | 依据路径值对重建同时支持 `Map` 与列表索引的嵌套结构。 |

## 说明

- `DictUtilsTest` 验证了复杂嵌套结构的叶子提取、列表索引路径、重建结果和拍平输出。
- `rebuildDict(...)` 支持形如 `[0]` 的路径段，并在需要时自动扩容列表。
