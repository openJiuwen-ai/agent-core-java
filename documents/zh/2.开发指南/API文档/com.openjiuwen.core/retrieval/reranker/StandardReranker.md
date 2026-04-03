# com.openjiuwen.core.retrieval.reranker.StandardReranker

## 类 StandardReranker

```java
public class StandardReranker implements Reranker
```

标准远程重排器，通过 `/rerank` 接口为候选文本打分，再返回分数字典或排序后的结果列表。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StandardReranker(RerankerConfig config)` | 使用默认重试次数创建重排器。 |
| `public StandardReranker(RerankerConfig config, int maxRetries, Map<String, String> extraHeaders, HttpClient httpClient)` | 指定重试次数、额外请求头与 `HttpClient`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Double> rerankScores(String query, List<?> documents)` | 以默认 instruction 执行重排评分。 |
| `public Map<String, Double> rerankScores(String query, List<?> documents, Object instruct, Map<String, Object> options)` | 带 instruction 与选项执行评分。 |
| `public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK)` | 直接返回排序后的候选结果。 |

## 说明

- 默认端点为 `/rerank`，默认 instruction 为 `"Given a search query, retrieve relevant candidates that answer the query."`。
- 候选输入支持 `String`、`Document` 与 `RetrievalResult`。
- 测试确认：`apiBase` 尾部含 `/rerank` 时会自动规范化，并能解析 `results[index,relevance_score]` 返回值。
