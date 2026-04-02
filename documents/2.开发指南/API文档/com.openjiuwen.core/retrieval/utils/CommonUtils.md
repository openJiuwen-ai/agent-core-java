# com.openjiuwen.core.retrieval.utils.CommonUtils

## 类 CommonUtils

```java
public final class CommonUtils
```

通用去重工具，按调用方提供的键函数保留首次出现的数据项。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static <T, K> List<T> deduplicate(Iterable<T> data, Function<T, K> keyFn)` | 对输入数据执行稳定去重。 |

## 说明

- 输入为 `null` 时返回空列表。
