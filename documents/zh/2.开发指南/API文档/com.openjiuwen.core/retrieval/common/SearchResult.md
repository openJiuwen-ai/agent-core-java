# com.openjiuwen.core.retrieval.common.SearchResult

## 类 SearchResult

```java
public class SearchResult
```

搜索层原始结果模型，保存结果标识、文本、分数与元数据。

## 说明

- `id` 不能为空白，`text` 不能为空。
- `FusionUtils` 可直接对 `SearchResult` 列表执行 RRF 或加权融合。
