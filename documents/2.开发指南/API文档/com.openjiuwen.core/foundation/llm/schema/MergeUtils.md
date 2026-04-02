# com.openjiuwen.core.foundation.llm.schema.MergeUtils

## 类 MergeUtils

```java
public final class MergeUtils
```

提供消息片段或其他可合并对象的拼接辅助逻辑。

## 嵌套类型

| 签名 | 说明 |
| --- | --- |
| `public interface Mergeable<T> {` | 约定片段对象的可合并能力。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static Object mergeParserContent(Object left, Object right) {` | 执行 `mergeParserContent` 公开能力。 |
| `public static Map<String, Object> mergeMaps(Map<String, Object> left, Map<String, Object> right) {` | 执行 `mergeMaps` 公开能力。 |
| `public static <T> T mergeObjects(T left, T right) {` | 执行 `mergeObjects` 公开能力。 |

## 说明

- 所有签名均以当前 Java 源码为准。
- `MergeUtilsTest` 覆盖可合并片段与空值场景。
