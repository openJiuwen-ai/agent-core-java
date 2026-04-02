# com.openjiuwen.core.retrieval.indexing.processor.extractor.LLMTripleExtractor

## 类 LLMTripleExtractor

```java
public class LLMTripleExtractor extends Extractor
```

`LLMTripleExtractor` 使用 `BaseModelClient` 为每个 `TextChunk` 调用模型抽取三元组，并支持并发执行与 JSON 响应修复。

## 构造方法

- `public LLMTripleExtractor(BaseModelClient llmClient, String modelName)`：默认 `temperature = 0.0f`、`maxConcurrent = 50`。
- `public LLMTripleExtractor(BaseModelClient llmClient, String modelName, float temperature, int maxConcurrent)`：完整配置构造。

当 `llmClient == null` 时，构造阶段就会抛出 `RETRIEVAL_RETRIEVER_LLM_CLIENT_NOT_FOUND`。

## 公开方法

### `public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options)`

- 空输入返回空列表。
- 为每个 chunk 启动虚拟线程任务，并用 `Semaphore` 限制并发数。
- 任一 chunk 失败后，会收集失败的 `chunk_id` 并抛出 `RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR`。

## 响应解析规则

- 接受纯 JSON 数组。
- 也接受带 `triples` 字段的 JSON 对象。
- 若模型返回 Markdown 代码块，会自动剥离外层围栏。
- 会移除形如 `,}`、`,]` 的尾逗号。

## 相关测试

- `LLMTripleExtractorTest` 验证对象包装、`confidence` 解析、空列表返回与非法 JSON 异常路径。
