# com.openjiuwen.core.retrieval.utils.FusionUtils

## 类 FusionUtils

```java
public final class FusionUtils
```

检索结果融合工具，支持 RRF 融合与加权融合。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static List<RetrievalResult> rrfFusionRetrieval(List<List<RetrievalResult>> resultsList, int k)` | 对 `RetrievalResult` 列表执行 RRF 融合。 |
| `public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList, int k)` | 对 `SearchResult` 列表执行 RRF 融合。 |
| `public static List<RetrievalResult> rrfFusionRetrieval(List<List<RetrievalResult>> resultsList, RRFRankConfig config)` | 根据配置执行 RRF 融合。 |
| `public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList, RRFRankConfig config)` | 根据配置执行搜索结果 RRF 融合。 |
| `public static List<RetrievalResult> weightedFusionRetrieval(List<List<RetrievalResult>> resultsList, WeightedRankConfig config)` | 对 `RetrievalResult` 列表执行加权融合。 |
| `public static List<SearchResult> weightedFusionSearch(List<List<SearchResult>> resultsList, WeightedRankConfig config)` | 对 `SearchResult` 列表执行加权融合。 |

## 说明

- RRF 与加权融合都按结果文本 `text` 去重。
- 融合后的分数会直接写回结果对象。
- 测试确认：重复文本会被合并，结果按最终分数降序排列。
