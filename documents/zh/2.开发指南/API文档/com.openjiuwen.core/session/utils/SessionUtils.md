# com.openjiuwen.core.session.utils.SessionUtils

## 类 SessionUtils

```java
public final class SessionUtils
```

session 工具方法集合，负责嵌套路径解析、schema 取值、结构扩展与字典合并。

## 字段

| 签名 | 说明 |
| --- | --- |
| `public static final String NESTED_PATH_SPLIT = "."` | 嵌套路径的字段分隔符。 |
| `public static final String NESTED_PATH_LIST_SPLIT = "["` | 嵌套路径中的列表索引起始标记。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static boolean isRefPath(String path)` | 判断字符串是否形如 `${xxx.yyy}` 的引用路径。 |
| `public static String extractOriginKey(String key)` | 从引用结构中提取原始路径键，例如 `${start123.p2}` 提取为 `start123.p2`。 |
| `public static List<Object> splitNestedPath(String nestedKey)` | 把嵌套路径拆成字段名和列表索引片段。 |
| `public static Object getValueByNestedPath(String nestedKey, Map<String, Object> source)` | 按嵌套路径从源映射中读取值。 |
| `public static Object[] rootToPath(String nestedPath, Object source, boolean createIfAbsent)` | 从根对象导航到目标路径末端，返回 `[key, container]`。 |
| `public static void updateDict(Map<String, Object> update, Map<String, Object> source, boolean ignoreDelete)` | 把更新映射合并到源映射；源结构保持未嵌套，更新键允许使用嵌套路径。 |
| `public static void updateDict(Map<String, Object> update, Map<String, Object> source)` | 使用默认 `ignoreDelete = false` 合并更新映射。 |
| `public static void updateByKey(Object key, Object newValue, Object source)` | 按键把值写入容器；若旧值和新值都是 `Map`，会递归合并。 |
| `public static void deleteByKey(Object key, Object source)` | 从 `Map` 容器中删除指定键。 |
| `public static Object expandNestedStructure(Object data)` | 把带嵌套路径键的结构展开成真正的嵌套 `Map/List`。 |
| `public static Object getBySchema(Object schema, Map<String, Object> data)` | 按 schema 从数据中取值，支持字符串、列表和映射 schema。 |
| `public static Object getBySchema(Object schema, Map<String, Object> data, String nestedPath, boolean isRoot)` | 在可选前缀路径下按 schema 取值。 |
| `public static boolean safeExtendContainer(List<Object> container, int targetIndex, boolean isFinalIndex)` | 安全扩容列表，使目标索引可访问。 |
| `public static Object[] rootToIndex(List<Integer> indexes, List<Object> source, boolean createIfAbsent)` | 按索引路径在嵌套列表中导航，返回 `[adjustedIndex, container]`。 |

## 嵌套类型

| 签名 | 说明 |
| --- | --- |
| `public static final class EndFrame` | 表示流式输出结束标记的哨兵类型。 |

## 说明

- 相关测试：`SessionTest`、`SessionUtilsTest`。
- `EndFrame.MESSAGE` 常量固定为 `all streaming outputs finish`，用于表示流式输出已经结束。
