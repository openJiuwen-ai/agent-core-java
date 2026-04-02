# com.openjiuwen.core.retrieval.SimpleKnowledgeBase

## class SimpleKnowledgeBase

```java
public class SimpleKnowledgeBase extends KnowledgeBase
```

Standard chunk-based knowledge base.

## Constructors

| Signature | Description |
| --- | --- |
| `public SimpleKnowledgeBase(KnowledgeBaseConfig config)` | Create a new `SimpleKnowledgeBase` instance. |
| `public SimpleKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Indexer indexManager, BaseModelClient llmClient, Retriever retriever)` | Create a new `SimpleKnowledgeBase` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<String> addDocuments(List<Document> documents)` | Add documents. |
| `stats.put("index_exists", activeIndexManager.indexExists(chunkIndexName()))` | Execute `put`. |
| `protected Map<String, Object> optionsFrom(RetrievalConfig config)` | Execute `optionsFrom`. |
| `baseRetriever = switch (config.getIndexType())` | Execute `switch`. |
| `RetrievalConfig retrievalConfig = config != null ? config : new RetrievalConfig()` | Execute `RetrievalConfig`. |
| `public static List<MultiKBRetrievalResult> retrieveMultiKbWithSource(List<? extends KnowledgeBase> knowledgeBases, String query, RetrievalConfig config, Integer topK)` | Execute `retrieveMultiKbWithSource`. |
| `RetrievalConfig retrievalConfig = config != null ? config : new RetrievalConfig()` | Execute `RetrievalConfig`. |

## Notes

- Related tests: `KnowledgeBaseTest.java`, `MilvusKnowledgeBaseTest.java`, `PGVectorKnowledgeBaseTest.java`.
