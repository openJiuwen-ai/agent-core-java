# com.openjiuwen.core.retrieval.embedding.APIEmbedding

## 类 APIEmbedding

```java
public class APIEmbedding implements Embedding, AutoCloseable
```

通用 HTTP embedding 客户端，支持重试、批量并发、回调通知与多种响应格式解析。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public APIEmbedding(EmbeddingConfig config)` | 使用默认参数创建客户端。 |
| `public APIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent)` | 指定超时、重试、请求头、批量与并发参数。 |
| `public APIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent, HttpClient httpClient)` | 额外注入 `HttpClient`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<Float> embedQuery(String text)` | 对单条文本生成向量。 |
| `public List<Float> embedQuery(String text, Map<String, Object> options)` | 带选项生成向量。 |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | 批量生成向量。 |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options)` | 带选项批量生成向量。 |
| `public int getDimension()` | 返回向量维度；首次调用时会用 `"test"` 懒加载维度。 |
| `public int getMaxBatchSize()` | 返回允许的最大批次。 |
| `public void close()` | 关闭内部线程池。 |

## 说明

- 默认参数：`timeout = 60` 秒、`maxRetries = 3`、`maxBatchSize = 8`、`maxConcurrent = 50`。
- 默认请求头总是包含 `Content-Type: application/json`；若 `apiKey` 非空还会附加 `Authorization`。
- 批量接口会按有效批次大小拆分任务，并在每个批次完成后触发回调。
- 测试确认：支持 `embedding`、`embeddings` 与 `data[].embedding` 三类响应结构，并支持重试与维度缓存。
