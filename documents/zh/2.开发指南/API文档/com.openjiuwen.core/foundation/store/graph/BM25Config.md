# com.openjiuwen.core.foundation.store.graph.BM25Config

## class BM25Config

```java
public class BM25Config
```

BM25 参数配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `bm25B` | `double` | `0.75` | BM25 的长度归一化参数，要求位于 `0` 到 `1` 之间。 |
| `bm25K1` | `double` | `1.2` | BM25 的词频饱和参数，要求大于等于 `0`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BM25Config(double bm25B, double bm25K1)` | 显式指定 BM25 参数。 |
| `public BM25Config()` | 使用默认 BM25 参数。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public double getBm25B()` | 返回 `bm25B`。 |
| `public double getBm25K1()` | 返回 `bm25K1`。 |
