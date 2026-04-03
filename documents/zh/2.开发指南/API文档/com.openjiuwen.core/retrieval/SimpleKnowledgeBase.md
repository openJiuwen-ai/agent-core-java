# com.openjiuwen.core.retrieval.SimpleKnowledgeBase

## 类 SimpleKnowledgeBase

```java
public class SimpleKnowledgeBase extends KnowledgeBase
```

标准分块式知识库实现，支持向量、BM25 与混合检索，以及多知识库结果聚合。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SimpleKnowledgeBase(KnowledgeBaseConfig config)` | 仅根据配置创建实例。 |
| `public SimpleKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Indexer indexManager, BaseModelClient llmClient, Retriever retriever)` | 注入检索所需核心组件。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<String> addDocuments(List<Document> documents)` | 对文档分块并写入索引。 |
| `public List<RetrievalResult> retrieve(String query, RetrievalConfig config)` | 根据 `indexType` 执行 `vector`、`sparse` 或 `hybrid` 检索。 |
| `public boolean deleteDocuments(List<String> docIds)` | 删除指定文档。 |
| `public List<String> updateDocuments(List<Document> documents)` | 更新文档内容。 |
| `public Map<String, Object> getStatistics()` | 返回知识库统计信息。 |
| `public static List<String> retrieveMultiKb(List<? extends KnowledgeBase> knowledgeBases, String query, RetrievalConfig config, Integer topK)` | 在多个知识库上执行聚合检索并返回文本列表。 |
| `public static List<String> retrieveMultiKb(List<? extends KnowledgeBase> knowledgeBases, String query, int topK)` | 以简化参数执行多知识库聚合检索。 |
| `public static List<MultiKBRetrievalResult> retrieveMultiKbWithSource(List<? extends KnowledgeBase> knowledgeBases, String query, RetrievalConfig config, Integer topK)` | 返回带来源知识库信息的聚合结果。 |
| `public static List<MultiKBRetrievalResult> retrieveMultiKbWithSource(List<? extends KnowledgeBase> knowledgeBases, String query, int topK)` | 以简化参数执行带来源信息的聚合检索。 |

## 说明

- 写入与更新文档要求 `chunker != null`；测试确认缺失 `chunker` 时会抛出异常。
- 当 `strictValidation = true` 且存在 `vectorStore` 时，写入、删除与更新前会校验向量字段配置。
- 若未显式提供 `retriever`，会依据 `KnowledgeBaseConfig.indexType` 自动创建对应检索器。
- 测试确认：可以通过 `RetrievalConfig.scoreThreshold` 过滤结果；无关查询会返回空列表或较少结果而不会抛错。
- 多知识库聚合会按文本去重，并保留最高得分结果；`retrieveMultiKbWithSource(...)` 还会记录来源知识库标识。
