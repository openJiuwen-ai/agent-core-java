# com.openjiuwen.core.retrieval.reranker.Reranker

## 接口 Reranker

```java
public interface Reranker
```

重排器统一接口，支持输出重排后的 `RetrievalResult` 列表，也支持返回“候选标识 -> 分数”的映射。

## 方法

| 签名 | 说明 |
| --- | --- |
| `List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK)` | 对候选结果执行重排并返回前 `topK` 项。 |
| `default Map<String, Double> rerankScores(String query, List<?> documents)` | 以默认 `instruct = true` 执行重排评分。 |
| `default Map<String, Double> rerankScores(String query, List<?> documents, Object instruct, Map<String, Object> options)` | 带指令与选项执行重排评分。 |
