# com.openjiuwen.core.foundation.store.graph.GraphUtils

## class GraphUtils

```java
public final class GraphUtils
```

图存储辅助工具类，当前提供按批切分迭代器的能力。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n, boolean strict)` | 按固定大小把可迭代对象切分为多批。 |
| `public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n)` | 默认 `strict = false` 的便捷重载。 |

## 使用说明

- `n` 必须大于等于 `1`。
- 当 `strict = true` 且最后一批数量不足 `n` 时，会抛出 `IllegalArgumentException`。
