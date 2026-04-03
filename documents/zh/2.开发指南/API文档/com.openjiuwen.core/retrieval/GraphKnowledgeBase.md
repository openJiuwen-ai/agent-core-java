# com.openjiuwen.core.retrieval.GraphKnowledgeBase

## 类 GraphKnowledgeBase

```java
public class GraphKnowledgeBase extends KnowledgeBase
```

在普通知识库基础上增加三元组抽取、图索引与图扩展检索能力。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GraphKnowledgeBase(KnowledgeBaseConfig config)` | 仅用配置创建图知识库。 |
| `public GraphKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient, Retriever chunkRetriever, Retriever tripleRetriever)` | 注入分块检索器、三元组检索器与图模式所需组件。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<String> addDocuments(List<Document> documents)` | 写入文档并在启用图模式时建立三元组索引。 |
| `public List<RetrievalResult> retrieve(String query, RetrievalConfig config)` | 执行图检索或退回普通检索。 |
| `public boolean deleteDocuments(List<String> docIds)` | 删除文档及其图索引记录。 |
| `public List<String> updateDocuments(List<Document> documents)` | 先删除后重建文档索引。 |
| `public Map<String, Object> getStatistics()` | 返回包含 chunk 与 triple 索引信息的统计结果。 |
| `public void close()` | 关闭图检索器及其依赖组件。 |
| `public static List<String> retrieveMultiGraphKb(List<? extends KnowledgeBase> knowledgeBases, String query, RetrievalConfig config, Integer topK)` | 在多个图知识库上执行聚合检索。 |
| `public static List<MultiKBRetrievalResult> retrieveMultiGraphKbWithSource(List<? extends KnowledgeBase> knowledgeBases, String query, RetrievalConfig config, Integer topK)` | 返回带来源知识库信息的图检索聚合结果。 |

## 说明

- 启用图模式时会额外维护 `kb_<kbId>_triples` 索引；测试确认统计信息中会出现 `triple_index_info`。
- 若 `useGraph = true` 且未显式注入 `Extractor`，但存在 `llmClient`，会自动退回到 `LLMTripleExtractor`；两者都缺失时会报错。
- `RetrievalConfig.useGraph` 可以覆盖 `KnowledgeBaseConfig.useGraph` 的默认行为。
- 测试确认：`useGraph = false` 时该类仍可作为普通知识库工作；`close()` 会额外关闭缓存的 `GraphRetriever`、`chunkRetriever` 与 `tripleRetriever`。
