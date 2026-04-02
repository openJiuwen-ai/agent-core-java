# com.openjiuwen.core.retrieval.KnowledgeBase

## class KnowledgeBase

```java
public abstract class KnowledgeBase implements AutoCloseable
```

Abstract knowledge base.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `config` | `final KnowledgeBaseConfig` | `-` | config. |
| `vectorStore` | `VectorStore` | `-` | vector store. |
| `embedModel` | `Embedding` | `-` | embed model. |
| `parser` | `Parser` | `-` | parser. |
| `chunker` | `Chunker` | `-` | chunker. |
| `extractor` | `Extractor` | `-` | extractor. |
| `indexManager` | `Indexer` | `-` | index manager. |
| `autoResolvedIndexManager` | `boolean` | `-` | auto resolved index manager. |
| `llmClient` | `BaseModelClient` | `-` | llm client. |
| `retriever` | `Retriever` | `-` | retriever. |
| `strictValidation` | `boolean` | `true` | strict validation. |

## Constructors

| Signature | Description |
| --- | --- |
| `protected KnowledgeBase(KnowledgeBaseConfig config)` | Create a new `KnowledgeBase` instance. |
| `protected KnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient, Retriever retriever)` | Create a new `KnowledgeBase` instance. |
| `protected KnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient, Retriever retriever, boolean strictValidation)` | Create a new `KnowledgeBase` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `config.validate()` | Execute `validate`. |
| `public VectorStore getVectorStore()` | Return the vector store. |
| `public void setVectorStore(VectorStore vectorStore)` | Update the vector store. |
| `public void setEmbedModel(Embedding embedModel)` | Update the embed model. |
| `public Parser getParser()` | Return the parser. |
| `public void setParser(Parser parser)` | Update the parser. |
| `public Chunker getChunker()` | Return the chunker. |
| `public void setChunker(Chunker chunker)` | Update the chunker. |
| `public Extractor getExtractor()` | Return the extractor. |
| `public void setExtractor(Extractor extractor)` | Update the extractor. |
| `public Indexer getIndexManager()` | Return the index manager. |
| `public void setIndexManager(Indexer indexManager)` | Update the index manager. |
| `public BaseModelClient getLlmClient()` | Return the llm client. |
| `public void setLlmClient(BaseModelClient llmClient)` | Update the llm client. |
| `public Retriever getRetriever()` | Return the retriever. |
| `public void setRetriever(Retriever retriever)` | Update the retriever. |
| `public List<Document> parseFiles(List<String> filePaths)` | Parse files. |
| `public List<Document> parseFiles(List<String> filePaths, Map<String, Object> options)` | Parse files. |
| `public List<Document> parseUrls(List<String> urls, Map<String, Object> options)` | Parse urls. |
| `public void setStrictValidation(boolean strictValidation)` | Update the strict validation. |
| `public void deleteCollection(String collection)` | Delete a collection from current database. |
| `vectorStore.deleteTable(collection)` | Delete table. |
| `public abstract List<RetrievalResult> retrieve(String query, RetrievalConfig config)` | Execute `retrieve`. |
| `public abstract boolean deleteDocuments(List<String> docIds)` | Delete documents. |
| `public abstract List<String> updateDocuments(List<Document> documents)` | Execute `updateDocuments`. |
| `public abstract Map<String, Object> getStatistics()` | Return the statistics. |
| `public void close()` | Close held resources. |
| `protected void validateIndex()` | Execute `validateIndex`. |
| `compareConfig("database_name", vectorStore.getDatabaseName(), indexManager.getDatabaseName(), vectorStore, indexManager)` | Execute `compareConfig`. |
| `indexManager = IndexerFactory.createIndexer(vectorStore)` | Execute `createIndexer`. |

## Notes

- Related tests: `KnowledgeBaseTest.java`, `MilvusKnowledgeBaseTest.java`, `PGVectorKnowledgeBaseTest.java`.
