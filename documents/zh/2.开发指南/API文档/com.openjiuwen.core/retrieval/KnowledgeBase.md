# com.openjiuwen.core.retrieval.KnowledgeBase

## 类 KnowledgeBase

```java
public abstract class KnowledgeBase implements AutoCloseable
```

抽象知识库基类，统一封装配置校验、组件装配、文件与 URL 解析、索引管理以及资源关闭流程。

## 字段

| 声明 | 说明 |
| --- | --- |
| `protected final KnowledgeBaseConfig config` | 当前知识库配置。 |
| `protected VectorStore vectorStore` | 向量库存储实现。 |
| `protected Embedding embedModel` | embedding 模型。 |
| `protected Parser parser` | 文档解析器。 |
| `protected Chunker chunker` | 文本分块器。 |
| `protected Extractor extractor` | 三元组或结构化信息抽取器。 |
| `protected Indexer indexManager` | 索引管理器。 |
| `protected BaseModelClient llmClient` | 可选的 LLM 客户端。 |
| `protected Retriever retriever` | 可选的检索器实现。 |
| `protected boolean strictValidation = true` | 是否启用严格索引校验。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public KnowledgeBaseConfig getConfig()` | 返回知识库配置。 |
| `public VectorStore getVectorStore()` | 返回当前 `VectorStore`。 |
| `public void setVectorStore(VectorStore vectorStore)` | 更新 `VectorStore`，必要时清空自动推导的 `Indexer` 并重新校验。 |
| `public Embedding getEmbedModel()` | 返回 embedding 模型。 |
| `public void setEmbedModel(Embedding embedModel)` | 更新 embedding 模型。 |
| `public Parser getParser()` | 返回 `Parser`。 |
| `public void setParser(Parser parser)` | 更新 `Parser`。 |
| `public Chunker getChunker()` | 返回 `Chunker`。 |
| `public void setChunker(Chunker chunker)` | 更新 `Chunker`。 |
| `public Extractor getExtractor()` | 返回 `Extractor`。 |
| `public void setExtractor(Extractor extractor)` | 更新 `Extractor`。 |
| `public Indexer getIndexManager()` | 返回当前索引管理器；缺失时会尝试自动解析。 |
| `public void setIndexManager(Indexer indexManager)` | 显式设置索引管理器并重新校验。 |
| `public BaseModelClient getLlmClient()` | 返回 LLM 客户端。 |
| `public void setLlmClient(BaseModelClient llmClient)` | 更新 LLM 客户端。 |
| `public Retriever getRetriever()` | 返回当前检索器。 |
| `public void setRetriever(Retriever retriever)` | 更新检索器。 |
| `public List<Document> parseFiles(List<String> filePaths)` | 解析文件列表。 |
| `public List<Document> parseFiles(List<String> filePaths, Map<String, Object> options)` | 按选项解析文件列表。 |
| `public List<Document> parseUrls(List<String> urls)` | 解析 URL 列表。 |
| `public List<Document> parseUrls(List<String> urls, Map<String, Object> options)` | 按选项解析 URL 列表，仅处理 `parser.supports(url)` 为真的条目。 |
| `public boolean isStrictValidation()` | 返回严格校验开关。 |
| `public void setStrictValidation(boolean strictValidation)` | 更新严格校验开关。 |
| `public void deleteCollection(String collection)` | 删除当前数据库中的向量集合。 |
| `public abstract List<String> addDocuments(List<Document> documents)` | 向知识库写入文档。 |
| `public abstract List<RetrievalResult> retrieve(String query, RetrievalConfig config)` | 执行检索。 |
| `public abstract boolean deleteDocuments(List<String> docIds)` | 删除指定文档。 |
| `public abstract List<String> updateDocuments(List<Document> documents)` | 更新指定文档。 |
| `public abstract Map<String, Object> getStatistics()` | 返回知识库统计信息。 |
| `public void close()` | 关闭 `Retriever`、`VectorStore` 与 `Indexer`。 |

## 说明

- 构造时 `config` 不能为空，并会立即调用 `config.validate()`。
- 该抽象类只暴露受保护构造器，供 `SimpleKnowledgeBase`、`GraphKnowledgeBase` 等子类调用，因此文档不把它们列为公开构造方法。
- 当 `vectorStore` 与 `indexManager` 同时存在时，会比较 `database_name`、`distance_metric`、`text_field`、`vector_field`、`sparse_vector_field`、`metadata_field`、`doc_id_field`。
- `parseFiles(...)` 与 `parseUrls(...)` 都要求 `parser != null`；测试确认 `parseUrls(...)` 会跳过 `parser.supports(url)` 返回 `false` 的 URL。
- 测试确认：向量库与索引器配置不兼容时会抛出异常，`close()` 在存在或不存在底层组件时都可安全调用。
